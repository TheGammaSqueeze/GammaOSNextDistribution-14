/*
 * Copyright (C) 2019 - 2020 Intel Corporation
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
#include <stdlib.h>
#include <usfstl/list.h>
#include <usfstl/loop.h>
#include <usfstl/uds.h>
#include <sys/socket.h>
#include <sys/mman.h>
#include <sys/un.h>
#include <stdlib.h>
#include <errno.h>
#include <usfstl/vhost.h>
#include <linux/virtio_ring.h>
#include <linux/virtio_config.h>
#include <endian.h>

/* copied from uapi */
#define VIRTIO_F_VERSION_1		32

#define MAX_REGIONS 8
#define SG_STACK_PREALLOC 5

struct usfstl_vhost_user_dev_int {
	struct usfstl_list fds;
	struct usfstl_job irq_job;

	struct usfstl_loop_entry entry;

	struct usfstl_vhost_user_dev ext;

	unsigned int n_regions;
	struct vhost_user_region regions[MAX_REGIONS];
	int region_fds[MAX_REGIONS];
	void *region_vaddr[MAX_REGIONS];

	int req_fd;

	struct {
		struct usfstl_loop_entry entry;
		bool enabled;
		bool sleeping;
		bool triggered;
		uint64_t desc_guest_addr;
		uint64_t avail_guest_addr;
		uint64_t used_guest_addr;
		struct vring virtq;
		int call_fd;
		uint16_t last_avail_idx;
	} virtqs[];
};

#define CONV(bits)							\
static inline uint##bits##_t __attribute__((used))			\
cpu_to_virtio##bits(struct usfstl_vhost_user_dev_int *dev,		\
		    uint##bits##_t v)					\
{									\
	if (dev->ext.features & (1ULL << VIRTIO_F_VERSION_1))		\
		return htole##bits(v);					\
	return v;							\
}									\
static inline uint##bits##_t __attribute__((used))			\
virtio_to_cpu##bits(struct usfstl_vhost_user_dev_int *dev,		\
		    uint##bits##_t v)					\
{									\
	if (dev->ext.features & (1ULL << VIRTIO_F_VERSION_1))		\
		return le##bits##toh(v);				\
	return v;							\
}

CONV(16)
CONV(32)
CONV(64)

static struct usfstl_vhost_user_buf *
usfstl_vhost_user_get_virtq_buf(struct usfstl_vhost_user_dev_int *dev,
				unsigned int virtq_idx,
				struct usfstl_vhost_user_buf *fixed)
{
	struct usfstl_vhost_user_buf *buf = fixed;
	struct vring *virtq = &dev->virtqs[virtq_idx].virtq;
	uint16_t avail_idx = virtio_to_cpu16(dev, virtq->avail->idx);
	uint16_t idx, desc_idx;
	struct vring_desc *desc;
	unsigned int n_in = 0, n_out = 0;
	bool more;

	if (avail_idx == dev->virtqs[virtq_idx].last_avail_idx)
		return NULL;

	/* ensure we read the descriptor after checking the index */
	__sync_synchronize();

	idx = dev->virtqs[virtq_idx].last_avail_idx++;
	idx %= virtq->num;
	desc_idx = virtio_to_cpu16(dev, virtq->avail->ring[idx]);
	USFSTL_ASSERT(desc_idx < virtq->num);

	desc = &virtq->desc[desc_idx];
	do {
		more = virtio_to_cpu16(dev, desc->flags) & VRING_DESC_F_NEXT;

		if (virtio_to_cpu16(dev, desc->flags) & VRING_DESC_F_WRITE)
			n_in++;
		else
			n_out++;
		desc = &virtq->desc[virtio_to_cpu16(dev, desc->next)];
	} while (more);

	if (n_in > fixed->n_in_sg || n_out > fixed->n_out_sg) {
		size_t sz = sizeof(*buf);
		struct iovec *vec;

		sz += (n_in + n_out) * sizeof(*vec);

		buf = calloc(1, sz);
		if (!buf)
			return NULL;

		vec = (void *)(buf + 1);
		buf->in_sg = vec;
		buf->out_sg = vec + n_in;
		buf->allocated = true;
	}

	buf->n_in_sg = 0;
	buf->n_out_sg = 0;
	buf->idx = desc_idx;

	desc = &virtq->desc[desc_idx];
	do {
		struct iovec *vec;
		uint64_t addr;

		more = virtio_to_cpu16(dev, desc->flags) & VRING_DESC_F_NEXT;

		if (virtio_to_cpu16(dev, desc->flags) & VRING_DESC_F_WRITE) {
			vec = &buf->in_sg[buf->n_in_sg];
			buf->n_in_sg++;
		} else {
			vec = &buf->out_sg[buf->n_out_sg];
			buf->n_out_sg++;
		}

		addr = virtio_to_cpu64(dev, desc->addr);
		vec->iov_base = usfstl_vhost_phys_to_va(&dev->ext, addr);
		vec->iov_len = virtio_to_cpu32(dev, desc->len);

		desc = &virtq->desc[virtio_to_cpu16(dev, desc->next)];
	} while (more);

	return buf;
}

static void usfstl_vhost_user_free_buf(struct usfstl_vhost_user_buf *buf)
{
	if (buf->allocated)
		free(buf);
}

static void usfstl_vhost_user_readable_handler(struct usfstl_loop_entry *entry)
{
	usfstl_loop_unregister(entry);
	entry->fd = -1;
}

static int usfstl_vhost_user_read_msg(int fd, struct msghdr *msghdr)
{
	struct iovec msg_iov;
	struct msghdr hdr2 = {
		.msg_iov = &msg_iov,
		.msg_iovlen = 1,
		.msg_control = msghdr->msg_control,
		.msg_controllen = msghdr->msg_controllen,
	};
	struct vhost_user_msg_hdr *hdr;
	size_t i;
	size_t maxlen = 0;
	ssize_t len;
	ssize_t prev_datalen;
	size_t prev_iovlen;

	USFSTL_ASSERT(msghdr->msg_iovlen >= 1);
	USFSTL_ASSERT(msghdr->msg_iov[0].iov_len >= sizeof(*hdr));

	hdr = msghdr->msg_iov[0].iov_base;
	msg_iov.iov_base = hdr;
	msg_iov.iov_len = sizeof(*hdr);

	len = recvmsg(fd, &hdr2, 0);
	if (len < 0)
		return -errno;
	if (len == 0)
		return -ENOTCONN;

	for (i = 0; i < msghdr->msg_iovlen; i++)
		maxlen += msghdr->msg_iov[i].iov_len;
	maxlen -= sizeof(*hdr);

	USFSTL_ASSERT_EQ((int)len, (int)sizeof(*hdr), "%d");
	USFSTL_ASSERT(hdr->size <= maxlen);

	if (!hdr->size)
		return 0;

	prev_iovlen = msghdr->msg_iovlen;
	msghdr->msg_iovlen = 1;

	msghdr->msg_control = NULL;
	msghdr->msg_controllen = 0;
	msghdr->msg_iov[0].iov_base += sizeof(*hdr);
	prev_datalen = msghdr->msg_iov[0].iov_len;
	msghdr->msg_iov[0].iov_len = hdr->size;
	len = recvmsg(fd, msghdr, 0);

	/* restore just in case the user needs it */
	msghdr->msg_iov[0].iov_base -= sizeof(*hdr);
	msghdr->msg_iov[0].iov_len = prev_datalen;
	msghdr->msg_control = hdr2.msg_control;
	msghdr->msg_controllen = hdr2.msg_controllen;

	msghdr->msg_iovlen = prev_iovlen;

	if (len < 0)
		return -errno;
	if (len == 0)
		return -ENOTCONN;

	USFSTL_ASSERT_EQ(hdr->size, (uint32_t)len, "%u");

	return 0;
}

static void usfstl_vhost_user_send_msg(struct usfstl_vhost_user_dev_int *dev,
				       struct vhost_user_msg *msg)
{
	size_t msgsz = sizeof(msg->hdr) + msg->hdr.size;
	bool ack = dev->ext.protocol_features &
		   (1ULL << VHOST_USER_PROTOCOL_F_REPLY_ACK);
	ssize_t written;

	if (ack)
		msg->hdr.flags |= VHOST_USER_MSG_FLAGS_NEED_REPLY;

	written = write(dev->req_fd, msg, msgsz);
	USFSTL_ASSERT_EQ(written, (ssize_t)msgsz, "%zd");

	if (ack) {
		struct usfstl_loop_entry entry = {
			.fd = dev->req_fd,
			.priority = 0x7fffffff, // max
			.handler = usfstl_vhost_user_readable_handler,
		};
		struct iovec msg_iov = {
			.iov_base = msg,
			.iov_len = sizeof(*msg),
		};
		struct msghdr msghdr = {
			.msg_iovlen = 1,
			.msg_iov = &msg_iov,
		};

		/*
		 * Wait for the fd to be readable - we may have to
		 * handle other simulation (time) messages while
		 * waiting ...
		 */
		usfstl_loop_register(&entry);
		while (entry.fd != -1)
			usfstl_loop_wait_and_handle();
		USFSTL_ASSERT_EQ(usfstl_vhost_user_read_msg(dev->req_fd,
							    &msghdr),
				 0, "%d");
	}
}

static void usfstl_vhost_user_send_virtq_buf(struct usfstl_vhost_user_dev_int *dev,
					     struct usfstl_vhost_user_buf *buf,
					     int virtq_idx)
{
	struct vring *virtq = &dev->virtqs[virtq_idx].virtq;
	unsigned int idx, widx;
	int call_fd = dev->virtqs[virtq_idx].call_fd;
	ssize_t written;
	uint64_t e = 1;

	if (dev->ext.server->ctrl)
		usfstl_sched_ctrl_sync_to(dev->ext.server->ctrl);

	idx = virtio_to_cpu16(dev, virtq->used->idx);
	widx = idx + 1;

	idx %= virtq->num;
	virtq->used->ring[idx].id = cpu_to_virtio32(dev, buf->idx);
	virtq->used->ring[idx].len = cpu_to_virtio32(dev, buf->written);

	/* write buffers / used table before flush */
	__sync_synchronize();

	virtq->used->idx = cpu_to_virtio16(dev, widx);

	if (call_fd < 0 &&
	    dev->ext.protocol_features &
			(1ULL << VHOST_USER_PROTOCOL_F_INBAND_NOTIFICATIONS) &&
	    dev->ext.protocol_features &
			(1ULL << VHOST_USER_PROTOCOL_F_SLAVE_REQ)) {
		struct vhost_user_msg msg = {
			.hdr.request = VHOST_USER_SLAVE_VRING_CALL,
			.hdr.flags = VHOST_USER_VERSION,
			.hdr.size = sizeof(msg.payload.vring_state),
			.payload.vring_state = {
				.idx = virtq_idx,
			},
		};

		usfstl_vhost_user_send_msg(dev, &msg);
		return;
	}

	written = write(dev->virtqs[virtq_idx].call_fd, &e, sizeof(e));
	USFSTL_ASSERT_EQ(written, (ssize_t)sizeof(e), "%zd");
}

static void usfstl_vhost_user_handle_queue(struct usfstl_vhost_user_dev_int *dev,
					   unsigned int virtq_idx)
{
	/* preallocate on the stack for most cases */
	struct iovec in_sg[SG_STACK_PREALLOC] = { };
	struct iovec out_sg[SG_STACK_PREALLOC] = { };
	struct usfstl_vhost_user_buf _buf = {
		.in_sg = in_sg,
		.n_in_sg = SG_STACK_PREALLOC,
		.out_sg = out_sg,
		.n_out_sg = SG_STACK_PREALLOC,
	};
	struct usfstl_vhost_user_buf *buf;

	while ((buf = usfstl_vhost_user_get_virtq_buf(dev, virtq_idx, &_buf))) {
		dev->ext.server->ops->handle(&dev->ext, buf, virtq_idx);

		usfstl_vhost_user_send_virtq_buf(dev, buf, virtq_idx);
		usfstl_vhost_user_free_buf(buf);
	}
}

static void usfstl_vhost_user_job_callback(struct usfstl_job *job)
{
	struct usfstl_vhost_user_dev_int *dev = job->data;
	unsigned int virtq;

	for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
		if (!dev->virtqs[virtq].triggered)
			continue;
		dev->virtqs[virtq].triggered = false;

		usfstl_vhost_user_handle_queue(dev, virtq);
	}
}

static void usfstl_vhost_user_virtq_kick(struct usfstl_vhost_user_dev_int *dev,
					 unsigned int virtq)
{
	if (!(dev->ext.server->input_queues & (1ULL << virtq)))
		return;

	dev->virtqs[virtq].triggered = true;

	if (usfstl_job_scheduled(&dev->irq_job))
		return;

	if (!dev->ext.server->scheduler) {
		usfstl_vhost_user_job_callback(&dev->irq_job);
		return;
	}

	if (dev->ext.server->ctrl)
		usfstl_sched_ctrl_sync_from(dev->ext.server->ctrl);

	dev->irq_job.start = usfstl_sched_current_time(dev->ext.server->scheduler) +
			     dev->ext.server->interrupt_latency;
	usfstl_sched_add_job(dev->ext.server->scheduler, &dev->irq_job);
}

static void usfstl_vhost_user_virtq_fdkick(struct usfstl_loop_entry *entry)
{
	struct usfstl_vhost_user_dev_int *dev = entry->data;
	unsigned int virtq;
	uint64_t v;

	for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
		if (entry == &dev->virtqs[virtq].entry)
			break;
	}

	USFSTL_ASSERT(virtq < dev->ext.server->max_queues);

	USFSTL_ASSERT_EQ((int)read(entry->fd, &v, sizeof(v)),
		       (int)sizeof(v), "%d");

	usfstl_vhost_user_virtq_kick(dev, virtq);
}

static void usfstl_vhost_user_clear_mappings(struct usfstl_vhost_user_dev_int *dev)
{
	unsigned int idx;
	for (idx = 0; idx < MAX_REGIONS; idx++) {
		if (dev->region_vaddr[idx]) {
			munmap(dev->region_vaddr[idx],
			       dev->regions[idx].size + dev->regions[idx].mmap_offset);
			dev->region_vaddr[idx] = NULL;
		}

		if (dev->region_fds[idx] != -1) {
			close(dev->region_fds[idx]);
			dev->region_fds[idx] = -1;
		}
	}
}

static void usfstl_vhost_user_setup_mappings(struct usfstl_vhost_user_dev_int *dev)
{
	unsigned int idx;

	for (idx = 0; idx < dev->n_regions; idx++) {
		USFSTL_ASSERT(!dev->region_vaddr[idx]);

		/*
		 * Cannot rely on the offset being page-aligned, I think ...
		 * adjust for it later when we translate addresses instead.
		 */
		dev->region_vaddr[idx] = mmap(NULL,
					      dev->regions[idx].size +
					      dev->regions[idx].mmap_offset,
					      PROT_READ | PROT_WRITE, MAP_SHARED,
					      dev->region_fds[idx], 0);
		USFSTL_ASSERT(dev->region_vaddr[idx] != (void *)-1,
			      "mmap() failed (%d) for fd %d", errno, dev->region_fds[idx]);
	}
}

static void
usfstl_vhost_user_update_virtq_kick(struct usfstl_vhost_user_dev_int *dev,
				  unsigned int virtq, int fd)
{
	if (dev->virtqs[virtq].entry.fd != -1) {
		usfstl_loop_unregister(&dev->virtqs[virtq].entry);
		close(dev->virtqs[virtq].entry.fd);
	}

	if (fd != -1) {
		dev->virtqs[virtq].entry.fd = fd;
		usfstl_loop_register(&dev->virtqs[virtq].entry);
	}
}

static void usfstl_vhost_user_dev_free(struct usfstl_vhost_user_dev_int *dev)
{
	unsigned int virtq;

	usfstl_loop_unregister(&dev->entry);
	usfstl_sched_del_job(&dev->irq_job);

	for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
		usfstl_vhost_user_update_virtq_kick(dev, virtq, -1);
		if (dev->virtqs[virtq].call_fd != -1)
			close(dev->virtqs[virtq].call_fd);
	}

	usfstl_vhost_user_clear_mappings(dev);

	if (dev->req_fd != -1)
		close(dev->req_fd);

	if (dev->ext.server->ops->disconnected)
		dev->ext.server->ops->disconnected(&dev->ext);

	if (dev->entry.fd != -1)
		close(dev->entry.fd);

	free(dev);
}

static void usfstl_vhost_user_get_msg_fds(struct msghdr *msghdr,
					  int *outfds, int max_fds)
{
	struct cmsghdr *msg;
	int fds;

	for (msg = CMSG_FIRSTHDR(msghdr); msg; msg = CMSG_NXTHDR(msghdr, msg)) {
		if (msg->cmsg_level != SOL_SOCKET)
			continue;
		if (msg->cmsg_type != SCM_RIGHTS)
			continue;

		fds = (msg->cmsg_len - CMSG_LEN(0)) / sizeof(int);
		USFSTL_ASSERT(fds <= max_fds);
		memcpy(outfds, CMSG_DATA(msg), fds * sizeof(int));
		break;
	}
}

static void usfstl_vhost_user_handle_msg(struct usfstl_loop_entry *entry)
{
	struct usfstl_vhost_user_dev_int *dev;
	struct vhost_user_msg msg;
	uint8_t data[256]; // limits the config space size ...
	struct iovec msg_iov[3] = {
		[0] = {
			.iov_base = &msg.hdr,
			.iov_len = sizeof(msg.hdr),
		},
		[1] = {
			.iov_base = &msg.payload,
			.iov_len = sizeof(msg.payload),
		},
		[2] = {
			.iov_base = data,
			.iov_len = sizeof(data),
		},
	};
	uint8_t msg_control[CMSG_SPACE(sizeof(int) * MAX_REGIONS)] = { 0 };
	struct msghdr msghdr = {
		.msg_iov = msg_iov,
		.msg_iovlen = 3,
		.msg_control = msg_control,
		.msg_controllen = sizeof(msg_control),
	};
	ssize_t len;
	size_t reply_len = 0;
	unsigned int virtq;
	int fd;

	dev = container_of(entry, struct usfstl_vhost_user_dev_int, entry);

	if (usfstl_vhost_user_read_msg(entry->fd, &msghdr)) {
		usfstl_vhost_user_dev_free(dev);
		return;
	}
	len = msg.hdr.size;

	USFSTL_ASSERT((msg.hdr.flags & VHOST_USER_MSG_FLAGS_VERSION) ==
		    VHOST_USER_VERSION);

	switch (msg.hdr.request) {
	case VHOST_USER_GET_FEATURES:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		reply_len = sizeof(uint64_t);
		msg.payload.u64 = dev->ext.server->features;
		msg.payload.u64 |= 1ULL << VHOST_USER_F_PROTOCOL_FEATURES;
		break;
	case VHOST_USER_SET_FEATURES:
		USFSTL_ASSERT_EQ(len, (ssize_t)sizeof(msg.payload.u64), "%zd");
		dev->ext.features = msg.payload.u64;
		break;
	case VHOST_USER_SET_OWNER:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		/* nothing to be done */
		break;
	case VHOST_USER_SET_MEM_TABLE:
		USFSTL_ASSERT(len <= (int)sizeof(msg.payload.mem_regions));
		USFSTL_ASSERT(msg.payload.mem_regions.n_regions <= MAX_REGIONS);
		usfstl_vhost_user_clear_mappings(dev);
		memcpy(dev->regions, msg.payload.mem_regions.regions,
		       msg.payload.mem_regions.n_regions *
		       sizeof(dev->regions[0]));
		dev->n_regions = msg.payload.mem_regions.n_regions;
		usfstl_vhost_user_get_msg_fds(&msghdr, dev->region_fds, MAX_REGIONS);
		usfstl_vhost_user_setup_mappings(dev);
		break;
	case VHOST_USER_SET_VRING_NUM:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.vring_state));
		USFSTL_ASSERT(msg.payload.vring_state.idx <
			      dev->ext.server->max_queues);
		dev->virtqs[msg.payload.vring_state.idx].virtq.num =
			msg.payload.vring_state.num;
		break;
	case VHOST_USER_SET_VRING_ADDR:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.vring_addr));
		USFSTL_ASSERT(msg.payload.vring_addr.idx <=
			      dev->ext.server->max_queues);
		USFSTL_ASSERT_EQ(msg.payload.vring_addr.flags, (uint32_t)0, "0x%x");
		USFSTL_ASSERT(!dev->virtqs[msg.payload.vring_addr.idx].enabled);

		// Save the guest physical addresses to make snapshotting more convenient.
		dev->virtqs[msg.payload.vring_addr.idx].desc_guest_addr =
			usfstl_vhost_user_to_phys(&dev->ext, msg.payload.vring_addr.descriptor);
		dev->virtqs[msg.payload.vring_addr.idx].used_guest_addr =
			usfstl_vhost_user_to_phys(&dev->ext, msg.payload.vring_addr.used);
		dev->virtqs[msg.payload.vring_addr.idx].avail_guest_addr =
			usfstl_vhost_user_to_phys(&dev->ext, msg.payload.vring_addr.avail);

		dev->virtqs[msg.payload.vring_addr.idx].last_avail_idx = 0;
		dev->virtqs[msg.payload.vring_addr.idx].virtq.desc =
			usfstl_vhost_user_to_va(&dev->ext,
					      msg.payload.vring_addr.descriptor);
		dev->virtqs[msg.payload.vring_addr.idx].virtq.used =
			usfstl_vhost_user_to_va(&dev->ext,
					      msg.payload.vring_addr.used);
		dev->virtqs[msg.payload.vring_addr.idx].virtq.avail =
			usfstl_vhost_user_to_va(&dev->ext,
					      msg.payload.vring_addr.avail);
		USFSTL_ASSERT(dev->virtqs[msg.payload.vring_addr.idx].virtq.avail &&
			    dev->virtqs[msg.payload.vring_addr.idx].virtq.desc &&
			    dev->virtqs[msg.payload.vring_addr.idx].virtq.used);
		break;
	case VHOST_USER_SET_VRING_BASE:
		/* ignored - logging not supported */
		/*
		 * FIXME: our Linux UML virtio implementation
		 *        shouldn't send this
		 */
		break;
	case VHOST_USER_SET_VRING_KICK:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.u64));
		virtq = msg.payload.u64 & VHOST_USER_U64_VRING_IDX_MSK;
		USFSTL_ASSERT(virtq <= dev->ext.server->max_queues);
		if (msg.payload.u64 & VHOST_USER_U64_NO_FD)
			fd = -1;
		else
			usfstl_vhost_user_get_msg_fds(&msghdr, &fd, 1);
		usfstl_vhost_user_update_virtq_kick(dev, virtq, fd);
		break;
	case VHOST_USER_SET_VRING_CALL:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.u64));
		virtq = msg.payload.u64 & VHOST_USER_U64_VRING_IDX_MSK;
		USFSTL_ASSERT(virtq <= dev->ext.server->max_queues);
		if (dev->virtqs[virtq].call_fd != -1)
			close(dev->virtqs[virtq].call_fd);
		if (msg.payload.u64 & VHOST_USER_U64_NO_FD)
			dev->virtqs[virtq].call_fd = -1;
		else
			usfstl_vhost_user_get_msg_fds(&msghdr,
						    &dev->virtqs[virtq].call_fd,
						    1);
		break;
	case VHOST_USER_GET_PROTOCOL_FEATURES:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		reply_len = sizeof(uint64_t);
		msg.payload.u64 = dev->ext.server->protocol_features;
		if (dev->ext.server->config && dev->ext.server->config_len)
			msg.payload.u64 |= 1ULL << VHOST_USER_PROTOCOL_F_CONFIG;
		msg.payload.u64 |= 1ULL << VHOST_USER_PROTOCOL_F_SLAVE_REQ;
		msg.payload.u64 |= 1ULL << VHOST_USER_PROTOCOL_F_SLAVE_SEND_FD;
		msg.payload.u64 |= 1ULL << VHOST_USER_PROTOCOL_F_REPLY_ACK;
		break;
	case VHOST_USER_SET_VRING_ENABLE:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.vring_state));
		USFSTL_ASSERT(msg.payload.vring_state.idx <
			      dev->ext.server->max_queues);
		dev->virtqs[msg.payload.vring_state.idx].enabled =
			msg.payload.vring_state.num;
		break;
	case VHOST_USER_SET_PROTOCOL_FEATURES:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.u64));
		dev->ext.protocol_features = msg.payload.u64;
		break;
	case VHOST_USER_SET_SLAVE_REQ_FD:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		if (dev->req_fd != -1)
			close(dev->req_fd);
		usfstl_vhost_user_get_msg_fds(&msghdr, &dev->req_fd, 1);
		USFSTL_ASSERT(dev->req_fd != -1);
		break;
	case VHOST_USER_GET_CONFIG:
		USFSTL_ASSERT(len == (int)(sizeof(msg.payload.cfg_space) +
					msg.payload.cfg_space.size));
		USFSTL_ASSERT(dev->ext.server->config && dev->ext.server->config_len);
		USFSTL_ASSERT(msg.payload.cfg_space.offset == 0);
		USFSTL_ASSERT(msg.payload.cfg_space.size <= dev->ext.server->config_len);
		msg.payload.cfg_space.flags = 0;
		msg_iov[1].iov_len = sizeof(msg.payload.cfg_space);
		msg_iov[2].iov_base = (void *)dev->ext.server->config;
		reply_len = len;
		break;
	case VHOST_USER_VRING_KICK:
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.vring_state));
		USFSTL_ASSERT(msg.payload.vring_state.idx <
			      dev->ext.server->max_queues);
		USFSTL_ASSERT(msg.payload.vring_state.num == 0);
		usfstl_vhost_user_virtq_kick(dev, msg.payload.vring_state.idx);
		break;
	case VHOST_USER_GET_SHARED_MEMORY_REGIONS:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		reply_len = sizeof(uint64_t);
		msg.payload.u64 = 0;
		break;
	case VHOST_USER_SLEEP:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		USFSTL_ASSERT_EQ(dev->ext.server->max_queues, NUM_SNAPSHOT_QUEUES, "%d");
		for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
			if (dev->virtqs[virtq].enabled) {
				dev->virtqs[virtq].enabled = false;
				dev->virtqs[virtq].sleeping = true;
				usfstl_loop_unregister(&dev->virtqs[virtq].entry);
			}
		}
		msg.payload.i8 = 1; // success
		reply_len = sizeof(msg.payload.i8);
		break;
	case VHOST_USER_WAKE:
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		USFSTL_ASSERT_EQ(dev->ext.server->max_queues, NUM_SNAPSHOT_QUEUES, "%d");
		// enable previously enabled queues on wake
		for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
			if (dev->virtqs[virtq].sleeping) {
				dev->virtqs[virtq].enabled = true;
				dev->virtqs[virtq].sleeping = false;
				usfstl_loop_register(&dev->virtqs[virtq].entry);
				// TODO: is this needed?
				usfstl_vhost_user_virtq_kick(dev, virtq);
			}
		}
		msg.payload.i8 = 1; // success
		reply_len = sizeof(msg.payload.i8);
		break;
	case VHOST_USER_SNAPSHOT: {
		USFSTL_ASSERT_EQ(len, (ssize_t)0, "%zd");
		USFSTL_ASSERT_EQ(dev->ext.server->max_queues, NUM_SNAPSHOT_QUEUES, "%d");
		for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
			struct vring_snapshot* snapshot = &msg.payload.snapshot_response.snapshot.vrings[virtq];
			snapshot->enabled = dev->virtqs[virtq].enabled;
			snapshot->sleeping = dev->virtqs[virtq].sleeping;
			snapshot->triggered = dev->virtqs[virtq].triggered;
			snapshot->num = dev->virtqs[virtq].virtq.num;
			snapshot->desc_guest_addr = dev->virtqs[virtq].desc_guest_addr;
			snapshot->avail_guest_addr = dev->virtqs[virtq].avail_guest_addr;
			snapshot->used_guest_addr = dev->virtqs[virtq].used_guest_addr;
			snapshot->last_avail_idx = dev->virtqs[virtq].last_avail_idx;
		}
		msg.payload.snapshot_response.bool_store = 1;
		reply_len = (int)sizeof(msg.payload.snapshot_response);
		break;
	}
	case VHOST_USER_RESTORE: {
		int *fds;
		USFSTL_ASSERT(len == (int)sizeof(msg.payload.restore_request));
		USFSTL_ASSERT_EQ(dev->ext.server->max_queues, NUM_SNAPSHOT_QUEUES, "%d");

		fds = (int*)malloc(dev->ext.server->max_queues * sizeof(int));
		for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
			fds[virtq] = -1;
		}
		usfstl_vhost_user_get_msg_fds(&msghdr, fds, 2);

		for (virtq = 0; virtq < dev->ext.server->max_queues; virtq++) {
			const struct vring_snapshot* snapshot = &msg.payload.restore_request.snapshot.vrings[virtq];
			dev->virtqs[virtq].enabled = snapshot->enabled;
			dev->virtqs[virtq].sleeping = snapshot->sleeping;
			dev->virtqs[virtq].triggered = snapshot->triggered;
			dev->virtqs[virtq].virtq.num = snapshot->num;
			dev->virtqs[virtq].desc_guest_addr = snapshot->desc_guest_addr;
			dev->virtqs[virtq].avail_guest_addr = snapshot->avail_guest_addr;
			dev->virtqs[virtq].used_guest_addr = snapshot->used_guest_addr;
			dev->virtqs[virtq].last_avail_idx = snapshot->last_avail_idx;

			dev->virtqs[virtq].entry.fd = fds[virtq];

			// Translate vring guest physical addresses.
			dev->virtqs[virtq].virtq.desc = usfstl_vhost_phys_to_va(&dev->ext, dev->virtqs[virtq].desc_guest_addr);
			dev->virtqs[virtq].virtq.used = usfstl_vhost_phys_to_va(&dev->ext, dev->virtqs[virtq].used_guest_addr);
			dev->virtqs[virtq].virtq.avail = usfstl_vhost_phys_to_va(&dev->ext, dev->virtqs[virtq].avail_guest_addr);
			USFSTL_ASSERT(dev->virtqs[virtq].virtq.avail &&
				      dev->virtqs[virtq].virtq.desc &&
				      dev->virtqs[virtq].virtq.used);
		}

		free(fds);

		msg.payload.i8 = 1; // success
		reply_len = sizeof(msg.payload.i8);
		break;
	}
	default:
		USFSTL_ASSERT(0, "Unsupported message: %d\n", msg.hdr.request);
	}

	if (reply_len || (msg.hdr.flags & VHOST_USER_MSG_FLAGS_NEED_REPLY)) {
		size_t i, tmp;

		if (!reply_len) {
			msg.payload.u64 = 0;
			reply_len = sizeof(uint64_t);
		}

		msg.hdr.size = reply_len;
		msg.hdr.flags &= ~VHOST_USER_MSG_FLAGS_NEED_REPLY;
		msg.hdr.flags |= VHOST_USER_MSG_FLAGS_REPLY;

		msghdr.msg_control = NULL;
		msghdr.msg_controllen = 0;

		reply_len += sizeof(msg.hdr);

		tmp = reply_len;
		for (i = 0; tmp && i < msghdr.msg_iovlen; i++) {
			if (tmp <= msg_iov[i].iov_len)
				msg_iov[i].iov_len = tmp;
			tmp -= msg_iov[i].iov_len;
		}
		msghdr.msg_iovlen = i;

		while (reply_len) {
			len = sendmsg(entry->fd, &msghdr, 0);
			if (len < 0) {
				usfstl_vhost_user_dev_free(dev);
				return;
			}
			USFSTL_ASSERT(len != 0);
			reply_len -= len;

			for (i = 0; len && i < msghdr.msg_iovlen; i++) {
				unsigned int rm = len;

				if (msg_iov[i].iov_len <= (size_t)len)
					rm = msg_iov[i].iov_len;
				len -= rm;
				msg_iov[i].iov_len -= rm;
				msg_iov[i].iov_base += rm;
			}
		}
	}
}

static void usfstl_vhost_user_connected(int fd, void *data)
{
	struct usfstl_vhost_user_server *server = data;
	struct usfstl_vhost_user_dev_int *dev;
	unsigned int i;

	dev = calloc(1, sizeof(*dev) +
			sizeof(dev->virtqs[0]) * server->max_queues);

	USFSTL_ASSERT(dev);

	for (i = 0; i < server->max_queues; i++) {
		dev->virtqs[i].call_fd = -1;
		dev->virtqs[i].entry.fd = -1;
		dev->virtqs[i].entry.data = dev;
		dev->virtqs[i].entry.handler = usfstl_vhost_user_virtq_fdkick;
	}

	for (i = 0; i < MAX_REGIONS; i++)
		dev->region_fds[i] = -1;
	dev->req_fd = -1;

	dev->ext.server = server;
	dev->irq_job.data = dev;
	dev->irq_job.name = "vhost-user-irq";
	dev->irq_job.priority = 0x10000000;
	dev->irq_job.callback = usfstl_vhost_user_job_callback;
	usfstl_list_init(&dev->fds);

	if (server->ops->connected)
		server->ops->connected(&dev->ext);

	dev->entry.fd = fd;
	dev->entry.handler = usfstl_vhost_user_handle_msg;

	usfstl_loop_register(&dev->entry);
}

void usfstl_vhost_user_server_start(struct usfstl_vhost_user_server *server)
{
	USFSTL_ASSERT(server->ops);
	USFSTL_ASSERT(server->socket);

	usfstl_uds_create(server->socket, usfstl_vhost_user_connected, server);
}

void usfstl_vhost_user_server_stop(struct usfstl_vhost_user_server *server)
{
	usfstl_uds_remove(server->socket);
}

void usfstl_vhost_user_dev_notify(struct usfstl_vhost_user_dev *extdev,
				  unsigned int virtq_idx,
				  const uint8_t *data, size_t datalen)
{
	struct usfstl_vhost_user_dev_int *dev;
	/* preallocate on the stack for most cases */
	struct iovec in_sg[SG_STACK_PREALLOC] = { };
	struct iovec out_sg[SG_STACK_PREALLOC] = { };
	struct usfstl_vhost_user_buf _buf = {
		.in_sg = in_sg,
		.n_in_sg = SG_STACK_PREALLOC,
		.out_sg = out_sg,
		.n_out_sg = SG_STACK_PREALLOC,
	};
	struct usfstl_vhost_user_buf *buf;

	dev = container_of(extdev, struct usfstl_vhost_user_dev_int, ext);

	USFSTL_ASSERT(virtq_idx <= dev->ext.server->max_queues);

	if (!dev->virtqs[virtq_idx].enabled)
		return;

	buf = usfstl_vhost_user_get_virtq_buf(dev, virtq_idx, &_buf);
	if (!buf)
		return;

	USFSTL_ASSERT(buf->n_in_sg && !buf->n_out_sg);
	iov_fill(buf->in_sg, buf->n_in_sg, data, datalen);
	buf->written = datalen;

	usfstl_vhost_user_send_virtq_buf(dev, buf, virtq_idx);
	usfstl_vhost_user_free_buf(buf);
}

void usfstl_vhost_user_config_changed(struct usfstl_vhost_user_dev *dev)
{
	struct usfstl_vhost_user_dev_int *idev;
	struct vhost_user_msg msg = {
		.hdr.request = VHOST_USER_SLAVE_CONFIG_CHANGE_MSG,
		.hdr.flags = VHOST_USER_VERSION,
	};

	idev = container_of(dev, struct usfstl_vhost_user_dev_int, ext);

	if (!(idev->ext.protocol_features &
			(1ULL << VHOST_USER_PROTOCOL_F_CONFIG)))
		return;

	usfstl_vhost_user_send_msg(idev, &msg);
}

void *usfstl_vhost_user_to_va(struct usfstl_vhost_user_dev *extdev, uint64_t addr)
{
	struct usfstl_vhost_user_dev_int *dev;
	unsigned int region;

	dev = container_of(extdev, struct usfstl_vhost_user_dev_int, ext);

	for (region = 0; region < dev->n_regions; region++) {
		if (addr >= dev->regions[region].user_addr &&
		    addr < dev->regions[region].user_addr +
			   dev->regions[region].size)
			return (uint8_t *)dev->region_vaddr[region] +
			       (addr -
				dev->regions[region].user_addr +
				dev->regions[region].mmap_offset);
	}
	USFSTL_ASSERT(0, "cannot translate user address %"PRIx64"\n", addr);
	return NULL;
}

uint64_t usfstl_vhost_user_to_phys(struct usfstl_vhost_user_dev *extdev, uint64_t addr)
{
	struct usfstl_vhost_user_dev_int *dev;
	unsigned int region;

	dev = container_of(extdev, struct usfstl_vhost_user_dev_int, ext);

	for (region = 0; region < dev->n_regions; region++) {
		if (addr >= dev->regions[region].user_addr &&
		    addr < dev->regions[region].user_addr +
			   dev->regions[region].size)
			return addr -
				dev->regions[region].user_addr +
				dev->regions[region].guest_phys_addr;
	}
	USFSTL_ASSERT(0, "cannot translate user address %"PRIx64"\n", addr);
	return 0;
}

void *usfstl_vhost_phys_to_va(struct usfstl_vhost_user_dev *extdev, uint64_t addr)
{
	struct usfstl_vhost_user_dev_int *dev;
	unsigned int region;

	dev = container_of(extdev, struct usfstl_vhost_user_dev_int, ext);

	for (region = 0; region < dev->n_regions; region++) {
		if (addr >= dev->regions[region].guest_phys_addr &&
		    addr < dev->regions[region].guest_phys_addr +
			   dev->regions[region].size)
			return (uint8_t *)dev->region_vaddr[region] +
			       (addr -
				dev->regions[region].guest_phys_addr +
				dev->regions[region].mmap_offset);
	}

	USFSTL_ASSERT(0, "cannot translate physical address %"PRIx64"\n", addr);
	return NULL;
}

size_t iov_len(struct iovec *sg, unsigned int nsg)
{
	size_t len = 0;
	unsigned int i;

	for (i = 0; i < nsg; i++)
		len += sg[i].iov_len;

	return len;
}

size_t iov_fill(struct iovec *sg, unsigned int nsg,
		const void *_buf, size_t buflen)
{
	const char *buf = _buf;
	unsigned int i;
	size_t copied = 0;

#define min(a, b) ({ typeof(a) _a = (a); typeof(b) _b = (b); _a < _b ? _a : _b; })
	for (i = 0; buflen && i < nsg; i++) {
		size_t cpy = min(buflen, sg[i].iov_len);

		memcpy(sg[i].iov_base, buf, cpy);
		buflen -= cpy;
		copied += cpy;
		buf += cpy;
	}

	return copied;
}

size_t iov_read(void *_buf, size_t buflen,
		struct iovec *sg, unsigned int nsg)
{
	char *buf = _buf;
	unsigned int i;
	size_t copied = 0;

#define min(a, b) ({ typeof(a) _a = (a); typeof(b) _b = (b); _a < _b ? _a : _b; })
	for (i = 0; buflen && i < nsg; i++) {
		size_t cpy = min(buflen, sg[i].iov_len);

		memcpy(buf, sg[i].iov_base, cpy);
		buflen -= cpy;
		copied += cpy;
		buf += cpy;
	}

	return copied;
}
