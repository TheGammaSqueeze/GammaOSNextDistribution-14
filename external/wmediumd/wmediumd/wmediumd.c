/*
 *	wmediumd, wireless medium simulator for mac80211_hwsim kernel module
 *	Copyright (c) 2011 cozybit Inc.
 *	Copyright (C) 2020 Intel Corporation
 *
 *	Author:	Javier Lopez	<jlopex@cozybit.com>
 *		Javier Cardona	<javier@cozybit.com>
 *
 *	This program is free software; you can redistribute it and/or
 *	modify it under the terms of the GNU General Public License
 *	as published by the Free Software Foundation; either version 2
 *	of the License, or (at your option) any later version.
 *
 *	This program is distributed in the hope that it will be useful,
 *	but WITHOUT ANY WARRANTY; without even the implied warranty of
 *	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *	GNU General Public License for more details.
 *
 *	You should have received a copy of the GNU General Public License
 *	along with this program; if not, write to the Free Software
 *	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 *	02110-1301, USA.
 */

#include <netlink/netlink.h>
#include <netlink/genl/genl.h>
#include <netlink/genl/ctrl.h>
#include <netlink/genl/family.h>
#include <assert.h>
#include <stdint.h>
#include <getopt.h>
#include <signal.h>
#include <math.h>
#include <sys/syslog.h>
#include <sys/timerfd.h>
#include <errno.h>
#include <limits.h>
#include <time.h>
#include <unistd.h>
#include <stdarg.h>
#include <endian.h>
#include <sys/msg.h>
#include <usfstl/loop.h>
#include <usfstl/sched.h>
#include <usfstl/schedctrl.h>
#include <usfstl/vhost.h>
#include <usfstl/uds.h>

#include "wmediumd.h"
#include "ieee80211.h"
#include "config.h"
#include "api.h"
#include "pmsr.h"
#include "grpc.h"

USFSTL_SCHEDULER(scheduler);

static void wmediumd_deliver_frame(struct usfstl_job *job);

enum {
	HWSIM_VQ_TX,
	HWSIM_VQ_RX,
	HWSIM_NUM_VQS,
};

static char *stpcpy_safe(char *dst, const char *src) {
	if (dst == NULL) {
		return NULL;
	}

	if (src == NULL) {
		*dst = '\0';
		return dst;
	}

	return stpcpy(dst, src);
}

static int strlen_safe(const char *src) {
	return src == NULL ? 0 : strlen(src);
}

static inline int div_round(int a, int b)
{
	return (a + b - 1) / b;
}

static inline u64 sec_to_ns(time_t sec)
{
	return sec * 1000 * 1000 * 1000;
}

static inline u64 ns_to_us(u64 ns)
{
	return ns / 1000L;
}

static inline u64 ts_to_ns(struct timespec ts)
{
	return sec_to_ns(ts.tv_sec) + ts.tv_nsec;
}

static inline double distance_to_rtt(double distance)
{
	const long light_speed = 299792458L;
	return distance / light_speed;
}

static inline double sec_to_ps(double sec)
{
	return sec * 1000 * 1000 * 1000;
}

static inline double meter_to_mm(double meter)
{
	return meter * 1000;
}

static inline int pkt_duration(int len, int rate)
{
	/* preamble + signal + t_sym * n_sym, rate in 100 kbps */
	return 16 + 4 + 4 * div_round((16 + 8 * len + 6) * 10, 4 * rate);
}

int w_logf(struct wmediumd *ctx, u8 level, const char *format, ...)
{
	va_list(args);
	va_start(args, format);
	if (ctx->log_lvl >= level) {
		return vprintf(format, args);
	}
	return -1;
}

int w_flogf(struct wmediumd *ctx, u8 level, FILE *stream, const char *format, ...)
{
	va_list(args);
	va_start(args, format);
	if (ctx->log_lvl >= level) {
		return vfprintf(stream, format, args);
	}
	return -1;
}

static void wqueue_init(struct wqueue *wqueue, int cw_min, int cw_max)
{
	INIT_LIST_HEAD(&wqueue->frames);
	wqueue->cw_min = cw_min;
	wqueue->cw_max = cw_max;
}

void station_init_queues(struct station *station)
{
	wqueue_init(&station->queues[IEEE80211_AC_BK], 15, 1023);
	wqueue_init(&station->queues[IEEE80211_AC_BE], 15, 1023);
	wqueue_init(&station->queues[IEEE80211_AC_VI], 7, 15);
	wqueue_init(&station->queues[IEEE80211_AC_VO], 3, 7);
}

static inline bool frame_has_a4(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	return (hdr->frame_control[1] & (FCTL_TODS | FCTL_FROMDS)) ==
		(FCTL_TODS | FCTL_FROMDS);
}

static inline bool frame_is_mgmt(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	return (hdr->frame_control[0] & FCTL_FTYPE) == FTYPE_MGMT;
}

static inline bool frame_is_data(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	return (hdr->frame_control[0] & FCTL_FTYPE) == FTYPE_DATA;
}

static inline bool frame_is_data_qos(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	return (hdr->frame_control[0] & (FCTL_FTYPE | STYPE_QOS_DATA)) ==
		(FTYPE_DATA | STYPE_QOS_DATA);
}

static inline bool frame_is_probe_req(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	return (hdr->frame_control[0] & (FCTL_FTYPE | STYPE_PROBE_REQ)) ==
		(FTYPE_MGMT | STYPE_PROBE_REQ);
}

static inline bool frame_has_zero_rates(const struct frame *frame)
{
	for (int i = 0; i < frame->tx_rates_count; i++) {
		if (frame->tx_rates[i].idx < 0)
			break;

		if (frame->tx_rates[i].count > 0) {
			return false;
		}
	}

	return true;
}

static inline void fill_tx_rates(struct frame *frame)
{
	if (frame->tx_rates_count <= 0) {
		return;
	}

	int max_index = get_max_index();

	/* Starting from OFDM rate (See per.c#rateset) */
	const int basic_rate_start = 4; /* 6 mbps */

	int i;
	int rate_count = min(max_index - basic_rate_start + 1, frame->tx_rates_count);

	for (i = 0; i < rate_count; i++) {
		frame->tx_rates[i].idx = basic_rate_start + rate_count - i - 1;
		frame->tx_rates[i].count = 4;
	}

	for (; i < frame->tx_rates_count; i++) {
		frame->tx_rates[i].idx = -1;
		frame->tx_rates[i].count = 0;
	}
}

static inline u8 *frame_get_qos_ctl(struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;

	if (frame_has_a4(frame))
		return (u8 *)hdr + 30;
	else
		return (u8 *)hdr + 24;
}

static enum ieee80211_ac_number frame_select_queue_80211(struct frame *frame)
{
	u8 *p;
	int priority;

	if (!frame_is_data(frame))
		return IEEE80211_AC_VO;

	if (!frame_is_data_qos(frame))
		return IEEE80211_AC_BE;

	p = frame_get_qos_ctl(frame);
	priority = *p & QOS_CTL_TAG1D_MASK;

	return ieee802_1d_to_ac[priority];
}

static double dBm_to_milliwatt(int decibel_intf)
{
#define INTF_LIMIT (31)
	int intf_diff = NOISE_LEVEL - decibel_intf;

	if (intf_diff >= INTF_LIMIT)
		return 0.001;

	if (intf_diff <= -INTF_LIMIT)
		return 1000.0;

	return pow(10.0, -intf_diff / 10.0);
}

static double milliwatt_to_dBm(double value)
{
	return 10.0 * log10(value);
}

static int set_interference_duration(struct wmediumd *ctx, int src_idx,
				     int duration, int signal)
{
	int i;

	if (!ctx->intf)
		return 0;

	if (signal >= CCA_THRESHOLD)
		return 0;

	for (i = 0; i < ctx->num_stas; i++) {
		ctx->intf[ctx->num_stas * src_idx + i].duration += duration;
		// use only latest value
		ctx->intf[ctx->num_stas * src_idx + i].signal = signal;
	}

	return 1;
}

static int get_signal_offset_by_interference(struct wmediumd *ctx, int src_idx,
					     int dst_idx)
{
	int i;
	double intf_power;

	if (!ctx->intf)
		return 0;

	intf_power = 0.0;
	for (i = 0; i < ctx->num_stas; i++) {
		if (i == src_idx || i == dst_idx)
			continue;
		if (drand48() < ctx->intf[i * ctx->num_stas + dst_idx].prob_col)
			intf_power += dBm_to_milliwatt(
				ctx->intf[i * ctx->num_stas + dst_idx].signal);
	}

	if (intf_power <= 1.0)
		return 0;

	return (int)(milliwatt_to_dBm(intf_power) + 0.5);
}

static bool is_multicast_ether_addr(const u8 *addr)
{
	return 0x01 & addr[0];
}

static struct station *get_station_by_addr(struct wmediumd *ctx, u8 *addr)
{
	struct station *station;

	list_for_each_entry(station, &ctx->stations, list) {
		if (memcmp(station->addr, addr, ETH_ALEN) == 0)
			return station;
	}
	return NULL;
}

static bool station_has_addr(struct station *station, const u8 *addr)
{
	unsigned int i;

	if (memcmp(station->addr, addr, ETH_ALEN) == 0)
		return true;

	for (i = 0; i < station->n_addrs; i++) {
		if (memcmp(station->addrs[i].addr, addr, ETH_ALEN) == 0)
			return true;
	}

	return false;
}

static struct station *get_station_by_used_addr(struct wmediumd *ctx, u8 *addr)
{
	struct station *station;

	list_for_each_entry(station, &ctx->stations, list) {
		if (station_has_addr(station, addr))
			return station;
	}
	return NULL;
}

static void wmediumd_wait_for_client_ack(struct wmediumd *ctx,
					 struct client *client)
{
	client->wait_for_ack = true;

	while (client->wait_for_ack)
		usfstl_loop_wait_and_handle();
}

static void wmediumd_remove_client(struct wmediumd *ctx, struct client *client);

static void wmediumd_notify_frame_start(struct usfstl_job *job)
{
	struct frame *frame = container_of(job, struct frame, start_job);
	struct wmediumd *ctx = job->data;
	struct client *client, *tmp;
	struct {
		struct wmediumd_message_header hdr;
		struct wmediumd_tx_start start;
	} __attribute__((packed)) msg = {
		.hdr.type = WMEDIUMD_MSG_TX_START,
		.hdr.data_len = sizeof(msg.start),
		.start.freq = frame->freq,
	};

	if (ctx->ctrl)
		usfstl_sched_ctrl_sync_to(ctx->ctrl);

	list_for_each_entry_safe(client, tmp, &ctx->clients, list) {
		if (!(client->flags & WMEDIUMD_CTL_NOTIFY_TX_START))
			continue;

		if (client == frame->src)
			msg.start.cookie = frame->cookie;
		else
			msg.start.cookie = 0;

		/* must be API socket since flags cannot otherwise be set */
		assert(client->type == CLIENT_API_SOCK);

		if (write(client->loop.fd, &msg, sizeof(msg)) < sizeof(msg)) {
			usfstl_loop_unregister(&client->loop);
			wmediumd_remove_client(ctx, client);
			continue;
		}

		wmediumd_wait_for_client_ack(ctx, client);
	}
}

static void log2pcap(struct wmediumd *ctx, struct frame *frame, uint64_t ts)
{
	struct {
		uint8_t it_version;
		uint8_t it_pad;
		uint16_t it_len;
		uint32_t it_present;
		struct {
			uint16_t freq, flags;
		} channel;
		uint8_t signal;
	} __attribute__((packed)) radiotap_hdr = {
		.it_len = htole16(sizeof(radiotap_hdr)),
		.it_present = htole32(1 << 3 /* channel */ |
				      1 << 5 /* signal dBm */),
		.channel.freq = htole16(frame->freq),
		.signal = frame->signal,
	};
	struct {
		uint32_t type, blocklen, ifidx, ts_hi, ts_lo, caplen, pktlen;
	} __attribute__((packed)) blockhdr = {
		.type = 6,
		.ts_hi = ts / (1ULL << 32),
		.ts_lo = ts,
		.caplen = frame->data_len + sizeof(radiotap_hdr),
		.pktlen = frame->data_len + sizeof(radiotap_hdr),
	};
	static const uint8_t pad[3];
	uint32_t sz, align;

	sz = blockhdr.caplen + sizeof(blockhdr) + sizeof(uint32_t);
	blockhdr.blocklen = (sz + 3) & ~3;
	align = blockhdr.blocklen - sz;

	fwrite(&blockhdr, sizeof(blockhdr), 1, ctx->pcap_file);
	fwrite(&radiotap_hdr, sizeof(radiotap_hdr), 1, ctx->pcap_file);
	fwrite(frame->data, frame->data_len, 1, ctx->pcap_file);
	fwrite(pad, align, 1, ctx->pcap_file);
	fwrite(&blockhdr.blocklen, sizeof(blockhdr.blocklen), 1, ctx->pcap_file);
	fflush(ctx->pcap_file);
}

static void queue_frame(struct wmediumd *ctx, struct station *station,
			struct frame *frame)
{
	struct ieee80211_hdr *hdr = (void *)frame->data;
	u8 *dest = hdr->addr1;
	uint64_t target;
	struct wqueue *queue;
	struct frame *tail;
	struct station *tmpsta, *deststa;
	int send_time;
	int cw;
	double error_prob;
	bool is_acked = false;
	bool noack = false;
	int i, j;
	int rate_idx;
	int ac;

	/* TODO configure phy parameters */
	int slot_time = 9;
	int sifs = 16;
	int difs = 2 * slot_time + sifs;

	int retries = 0;

	int ack_time_usec = pkt_duration(14, index_to_rate(0, frame->freq)) +
	                    sifs;

	/*
	 * To determine a frame's expiration time, we compute the
	 * number of retries we might have to make due to radio conditions
	 * or contention, and add backoff time accordingly.  To that, we
	 * add the expiration time of the previous frame in the queue.
	 */

	ac = frame_select_queue_80211(frame);
	queue = &station->queues[ac];

	/* try to "send" this frame at each of the rates in the rateset */
	send_time = 0;
	cw = queue->cw_min;

	int snr = SNR_DEFAULT;

	if (is_multicast_ether_addr(dest)) {
		deststa = NULL;
	} else {
		deststa = get_station_by_used_addr(ctx, dest);
		if (deststa) {
			snr = ctx->get_link_snr(ctx, station, deststa) -
				get_signal_offset_by_interference(ctx,
					station->index, deststa->index);
			snr += ctx->get_fading_signal(ctx);
		}
	}
	frame->signal = snr + NOISE_LEVEL;

	noack = is_multicast_ether_addr(dest);

	/*
	 * TODO(b/211353765) Remove this when fundamenal solution is applied
	 *
	 *   Temporary workaround for relaying probe_req frame.
	 */
	if (frame_is_probe_req(frame) && frame_has_zero_rates(frame)) {
		fill_tx_rates(frame);
	}

	double choice = drand48();

	for (i = 0; i < frame->tx_rates_count && !is_acked; i++) {

		rate_idx = frame->tx_rates[i].idx;

		/* no more rates in MRR */
		if (rate_idx < 0)
			break;

		error_prob = ctx->get_error_prob(ctx, snr, rate_idx,
						 frame->freq, frame->data_len,
						 station, deststa);
		for (j = 0; j < frame->tx_rates[i].count; j++) {
			send_time += difs + pkt_duration(frame->data_len,
				index_to_rate(rate_idx, frame->freq));

			retries++;

			/* skip ack/backoff/retries for noack frames */
			if (noack) {
				is_acked = true;
				break;
			}

			/* TODO TXOPs */

			/* backoff */
			if (j > 0) {
				send_time += (cw * slot_time) / 2;
				cw = (cw << 1) + 1;
				if (cw > queue->cw_max)
					cw = queue->cw_max;
			}

			send_time += ack_time_usec;

			if (choice > error_prob) {
				is_acked = true;
				break;
			}

			if (!use_fixed_random_value(ctx))
				choice = drand48();
		}
	}

	if (is_acked) {
		frame->tx_rates[i-1].count = j + 1;
		for (; i < frame->tx_rates_count; i++) {
			frame->tx_rates[i].idx = -1;
			frame->tx_rates[i].count = -1;
		}
		frame->flags |= HWSIM_TX_STAT_ACK;
	}

	/*
	 * delivery time starts after any equal or higher prio frame
	 * (or now, if none).
	 */
	target = scheduler.current_time;
	for (i = 0; i <= ac; i++) {
		list_for_each_entry(tmpsta, &ctx->stations, list) {
			tail = list_last_entry_or_null(&tmpsta->queues[i].frames,
						       struct frame, list);
			if (tail && target < tail->job.start)
				target = tail->job.start;
		}
	}

	if (ctx->pcap_file) {
		log2pcap(ctx, frame, target);

		if (is_acked && !noack) {
			struct {
				struct frame frame;
				uint16_t fc;
				uint16_t dur;
				uint8_t ra[6];
			} __attribute__((packed, aligned(8))) ack = {
				.fc = htole16(0xd4),
				.dur = htole16(ack_time_usec),
			};

			memcpy(&ack.frame, frame, sizeof(ack.frame));
			ack.frame.data_len = 10;
			memcpy(ack.ra, frame->data + 10, 6);

			log2pcap(ctx, &ack.frame,
				 target + send_time - ack_time_usec);
		}
	}

	target += send_time;

	frame->duration = send_time;
	frame->src = station->client;

	if (ctx->need_start_notify) {
		frame->start_job.start = target - send_time;
		frame->start_job.callback = wmediumd_notify_frame_start;
		frame->start_job.data = ctx;
		frame->start_job.name = "frame-start";
		usfstl_sched_add_job(&scheduler, &frame->start_job);
	}

	frame->job.start = target;
	frame->job.callback = wmediumd_deliver_frame;
	frame->job.data = ctx;
	frame->job.name = "frame";
	usfstl_sched_add_job(&scheduler, &frame->job);
	list_add_tail(&frame->list, &queue->frames);
}

static void wmediumd_send_to_client(struct wmediumd *ctx,
				    struct client *client,
				    struct nl_msg *msg)
{
	struct wmediumd_message_header hdr;
	size_t len;
	int ret;

	switch (client->type) {
	case CLIENT_NETLINK:
		ret = nl_send_auto_complete(ctx->sock, msg);
		if (ret < 0)
			w_logf(ctx, LOG_ERR, "%s: nl_send_auto failed\n", __func__);
		break;
	case CLIENT_VHOST_USER:
		len = nlmsg_total_size(nlmsg_datalen(nlmsg_hdr(msg)));
		usfstl_vhost_user_dev_notify(client->dev, HWSIM_VQ_RX,
					     (void *)nlmsg_hdr(msg), len);
		break;
	case CLIENT_API_SOCK:
		len = nlmsg_total_size(nlmsg_datalen(nlmsg_hdr(msg)));
		hdr.type = WMEDIUMD_MSG_NETLINK;
		hdr.data_len = len;

		if (write(client->loop.fd, &hdr, sizeof(hdr)) < sizeof(hdr))
			goto disconnect;

		if (write(client->loop.fd, (void *)nlmsg_hdr(msg), len) < len)
			goto disconnect;

		wmediumd_wait_for_client_ack(ctx, client);
		break;
	}

	return;

	disconnect:
	usfstl_loop_unregister(&client->loop);
	wmediumd_remove_client(ctx, client);
}

static void wmediumd_remove_client(struct wmediumd *ctx, struct client *client)
{
	struct frame *frame, *tmp;
	struct wqueue *queue;
	struct station *station;
	int ac;

	list_for_each_entry(station, &ctx->stations, list) {
		if (station->client == client)
			station->client = NULL;
	}

	list_for_each_entry(station, &ctx->stations, list) {
		for (ac = 0; ac < IEEE80211_NUM_ACS; ac++) {
			queue = &station->queues[ac];
			list_for_each_entry_safe(frame, tmp, &queue->frames,
						 list) {
				if (frame->src == client) {
					list_del(&frame->list);
					usfstl_sched_del_job(&frame->job);
					free(frame);
				}
			}
		}
	}

	if (!list_empty(&client->list))
		list_del(&client->list);
	list_add(&client->list, &ctx->clients_to_free);

	if (client->flags & WMEDIUMD_CTL_NOTIFY_TX_START)
		ctx->need_start_notify--;

	client->wait_for_ack = false;
}

/*
 * Report transmit status to the kernel.
 */
static void send_tx_info_frame_nl(struct wmediumd *ctx, struct frame *frame)
{
	struct nl_msg *msg;

	msg = nlmsg_alloc();
	if (!msg) {
		w_logf(ctx, LOG_ERR, "Error allocating new message MSG!\n");
		return;
	}

	if (genlmsg_put(msg, NL_AUTO_PID, NL_AUTO_SEQ, ctx->family_id,
			0, NLM_F_REQUEST, HWSIM_CMD_TX_INFO_FRAME,
			VERSION_NR) == NULL) {
		w_logf(ctx, LOG_ERR, "%s: genlmsg_put failed\n", __func__);
		goto out;
	}

	if (nla_put(msg, HWSIM_ATTR_ADDR_TRANSMITTER, ETH_ALEN,
		    frame->sender->hwaddr) ||
	    nla_put_u32(msg, HWSIM_ATTR_FLAGS, frame->flags) ||
	    nla_put_u32(msg, HWSIM_ATTR_SIGNAL, frame->signal) ||
	    nla_put(msg, HWSIM_ATTR_TX_INFO,
		    frame->tx_rates_count * sizeof(struct hwsim_tx_rate),
		    frame->tx_rates) ||
	    nla_put_u64(msg, HWSIM_ATTR_COOKIE, frame->cookie)) {
		w_logf(ctx, LOG_ERR, "%s: Failed to fill a payload\n", __func__);
		goto out;
	}

	if (ctx->ctrl)
		usfstl_sched_ctrl_sync_to(ctx->ctrl);
	wmediumd_send_to_client(ctx, frame->src, msg);

out:
	nlmsg_free(msg);
}

/*
 * Send a data frame to the kernel for reception at a specific radio.
 */
static void send_cloned_frame_msg(struct wmediumd *ctx, struct client *src,
				  struct station *dst, u8 *data, int data_len,
				  int rate_idx, int signal, int freq,
				  uint64_t cookie)
{
	struct client *client, *tmp;
	struct nl_msg *msg, *cmsg = NULL;

	msg = nlmsg_alloc();
	if (!msg) {
		w_logf(ctx, LOG_ERR, "Error allocating new message MSG!\n");
		return;
	}

	if (genlmsg_put(msg, NL_AUTO_PID, NL_AUTO_SEQ, ctx->family_id,
			0, NLM_F_REQUEST, HWSIM_CMD_FRAME,
			VERSION_NR) == NULL) {
		w_logf(ctx, LOG_ERR, "%s: genlmsg_put failed\n", __func__);
		goto out;
	}

	if (nla_put(msg, HWSIM_ATTR_ADDR_RECEIVER, ETH_ALEN,
		    dst->hwaddr) ||
	    nla_put(msg, HWSIM_ATTR_FRAME, data_len, data) ||
	    nla_put_u32(msg, HWSIM_ATTR_RX_RATE, 1) ||
	    nla_put_u32(msg, HWSIM_ATTR_FREQ, freq) ||
	    nla_put_u32(msg, HWSIM_ATTR_SIGNAL, signal)) {
		w_logf(ctx, LOG_ERR, "%s: Failed to fill a payload\n", __func__);
		goto out;
	}

	w_logf(ctx, LOG_DEBUG, "cloned msg dest " MAC_FMT " (radio: " MAC_FMT ") len %d\n",
		   MAC_ARGS(dst->addr), MAC_ARGS(dst->hwaddr), data_len);

	if (ctx->ctrl)
		usfstl_sched_ctrl_sync_to(ctx->ctrl);

	list_for_each_entry_safe(client, tmp, &ctx->clients, list) {
		if (client->flags & WMEDIUMD_CTL_RX_ALL_FRAMES) {
			if (src == client && !cmsg) {
				struct nlmsghdr *nlh = nlmsg_hdr(msg);

				cmsg = nlmsg_inherit(nlh);
				nlmsg_append(cmsg, nlmsg_data(nlh), nlmsg_datalen(nlh), 0);
				assert(nla_put_u64(cmsg, HWSIM_ATTR_COOKIE, cookie) == 0);
			}
			wmediumd_send_to_client(ctx, client,
						src == client ? cmsg : msg);
		} else if (!dst->client || dst->client == client) {
			wmediumd_send_to_client(ctx, client, msg);
		}
	}

out:
	nlmsg_free(msg);
	if (cmsg)
		nlmsg_free(cmsg);
}

static void wmediumd_deliver_frame(struct usfstl_job *job)
{
	struct wmediumd *ctx = job->data;
	struct frame *frame = container_of(job, struct frame, job);
	struct ieee80211_hdr *hdr = (void *) frame->data;
	struct station *station;
	u8 *dest = hdr->addr1;
	u8 *src = frame->sender->addr;

	list_del(&frame->list);

	if (frame->flags & HWSIM_TX_STAT_ACK) {
		/* rx the frame on the dest interface */
		list_for_each_entry(station, &ctx->stations, list) {
			if (memcmp(src, station->addr, ETH_ALEN) == 0)
				continue;

			if (is_multicast_ether_addr(dest) && station->client != NULL) {
				int snr, rate_idx, signal;
				double error_prob;

				/*
				 * we may or may not receive this based on
				 * reverse link from sender -- check for
				 * each receiver.
				 */
				snr = ctx->get_link_snr(ctx, frame->sender,
							station);
				snr += ctx->get_fading_signal(ctx);
				signal = snr + NOISE_LEVEL;
				if (signal < CCA_THRESHOLD)
					continue;

				if (set_interference_duration(ctx,
					frame->sender->index, frame->duration,
					signal))
					continue;

				snr -= get_signal_offset_by_interference(ctx,
					frame->sender->index, station->index);
				rate_idx = frame->tx_rates[0].idx;
				error_prob = ctx->get_error_prob(ctx,
					(double)snr, rate_idx, frame->freq,
					frame->data_len, frame->sender,
					station);

				if (drand48() <= error_prob) {
					w_logf(ctx, LOG_INFO, "Dropped mcast from "
						   MAC_FMT " to " MAC_FMT " at receiver\n",
						   MAC_ARGS(src), MAC_ARGS(station->addr));
					continue;
				}

				send_cloned_frame_msg(ctx, frame->sender->client,
						      station,
						      frame->data,
						      frame->data_len,
						      1, signal,
						      frame->freq,
						      frame->cookie);

			} else if (station_has_addr(station, dest)) {
				if (set_interference_duration(ctx,
					frame->sender->index, frame->duration,
					frame->signal))
					continue;

				send_cloned_frame_msg(ctx, frame->sender->client,
						      station,
						      frame->data,
						      frame->data_len,
						      1, frame->signal,
						      frame->freq,
						      frame->cookie);
			}
		}
	} else
		set_interference_duration(ctx, frame->sender->index,
					  frame->duration, frame->signal);

	send_tx_info_frame_nl(ctx, frame);

	free(frame);
}

static void wmediumd_intf_update(struct usfstl_job *job)
{
	struct wmediumd *ctx = job->data;
	int i, j;

	for (i = 0; i < ctx->num_stas; i++)
		for (j = 0; j < ctx->num_stas; j++) {
			if (i == j)
				continue;
			// probability is used for next calc
			ctx->intf[i * ctx->num_stas + j].prob_col =
				ctx->intf[i * ctx->num_stas + j].duration /
				(double)10000;
			ctx->intf[i * ctx->num_stas + j].duration = 0;
		}

	job->start += 10000;
	usfstl_sched_add_job(&scheduler, job);
}

static
int nl_err_cb(struct sockaddr_nl *nla, struct nlmsgerr *nlerr, void *arg)
{
	struct genlmsghdr *gnlh = nlmsg_data(&nlerr->msg);
	struct wmediumd *ctx = arg;

	w_flogf(ctx, LOG_ERR, stderr, "nl: cmd %d, seq %d: %s\n", gnlh->cmd,
			nlerr->msg.nlmsg_seq, strerror(abs(nlerr->error)));

	return NL_SKIP;
}

static int send_pmsr_result_ftm(struct nl_msg *msg,
				struct pmsr_request_ftm *req,
				struct wmediumd *ctx,
				struct station *sender,
				struct station *receiver)
{
	struct nlattr *ftm;
	int err;

	ftm = nla_nest_start(msg, NL80211_PMSR_TYPE_FTM);
	if (!ftm)
		return -ENOMEM;

	if (!receiver) {
		err = nla_put_u32(msg, NL80211_PMSR_FTM_RESP_ATTR_FAIL_REASON,
				  NL80211_PMSR_FTM_FAILURE_NO_RESPONSE);
		goto out;
	}

	double distance =
		sqrt(pow(sender->x - receiver->x, 2) + pow(sender->y - receiver->y, 2));
	double distance_in_mm = meter_to_mm(distance);
	double rtt_in_ps = sec_to_ps(distance_to_rtt(distance));
	if (distance_in_mm > UINT64_MAX || rtt_in_ps > UINT64_MAX) {
		w_logf(ctx, LOG_WARNING,
		       "%s: Devices are too far away", __func__);
		return nla_put_u32(msg, NL80211_PMSR_FTM_RESP_ATTR_FAIL_REASON,
				   NL80211_PMSR_FTM_FAILURE_NO_RESPONSE);
	}

	int rssi = receiver->tx_power -
		   ctx->calc_path_loss(NULL, sender, receiver);

	if (nla_put_u32(msg, NL80211_PMSR_FTM_RESP_ATTR_NUM_FTMR_ATTEMPTS,
			req->ftmr_retries) ||
		nla_put_u32(msg, NL80211_PMSR_FTM_RESP_ATTR_NUM_FTMR_SUCCESSES,
			req->ftmr_retries) ||
		nla_put_u8(msg, NL80211_PMSR_FTM_RESP_ATTR_NUM_BURSTS_EXP,
			req->num_bursts_exp) ||
		nla_put_u8(msg, NL80211_PMSR_FTM_RESP_ATTR_BURST_DURATION,
			 req->burst_duration) ||
		nla_put_u8(msg, NL80211_PMSR_FTM_RESP_ATTR_FTMS_PER_BURST,
			 req->ftms_per_burst) ||
		nla_put_s32(msg, NL80211_PMSR_FTM_RESP_ATTR_RSSI_AVG, rssi) ||
		nla_put_s32(msg, NL80211_PMSR_FTM_RESP_ATTR_RSSI_SPREAD, 0) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_RTT_AVG,
			  (uint64_t)rtt_in_ps) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_RTT_VARIANCE, 0) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_RTT_SPREAD, 0) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_DIST_AVG,
			 (uint64_t)distance_in_mm) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_DIST_VARIANCE, 0) ||
		nla_put_u64(msg, NL80211_PMSR_FTM_RESP_ATTR_DIST_SPREAD, 0)) {
		w_logf(ctx, LOG_ERR, "%s: Failed to fill a payload\n", __func__);
		return -ENOMEM;
	}

	if (req->request_lci && receiver->lci) {
		nla_put_string(msg, NL80211_PMSR_FTM_RESP_ATTR_LCI,
			       receiver->lci);
	}

	if (req->request_civicloc && receiver->civicloc) {
		nla_put_string(msg, NL80211_PMSR_FTM_RESP_ATTR_CIVICLOC,
			       receiver->civicloc);
	}

out:
	if (ftm)
		nla_nest_end(msg, ftm);

	return 0;
}

static int send_pmsr_result_peer(struct nl_msg *msg,
				 struct pmsr_request_peer *req,
				 struct wmediumd *ctx,
				 struct station *sender)
{
	struct nlattr *peer, *resp, *data;
	struct station *receiver;
	int status;
	struct timespec ts;
	u64 host_time_ns;
	u64 ap_tsf_us;
	int err;

	peer = nla_nest_start(msg, 1);
	if (!peer)
		return -ENOMEM;

	receiver = get_station_by_addr(ctx, req->addr);
	if (receiver)
		status = NL80211_PMSR_STATUS_SUCCESS;
	else {
		w_logf(ctx, LOG_WARNING, "%s: unknown pmsr target " MAC_FMT "\n",
		       __func__, MAC_ARGS(req->addr));
		status = NL80211_PMSR_STATUS_FAILURE;
	}

	if (clock_gettime(CLOCK_BOOTTIME, &ts)) {
		w_logf(ctx, LOG_ERR, "%s: clock_gettime() failed\n", __func__);
		return -EINVAL;
	}

	host_time_ns = ts_to_ns(ts);
	ap_tsf_us = ns_to_us(ts_to_ns(ts));

	err = nla_put(msg, NL80211_PMSR_PEER_ATTR_ADDR, ETH_ALEN, req->addr);
	if (err)
		return err;

	resp = nla_nest_start(msg, NL80211_PMSR_PEER_ATTR_RESP);
	if (!resp)
		return -ENOMEM;

	if (nla_put_u32(msg, NL80211_PMSR_RESP_ATTR_STATUS, status) ||
		nla_put_u64(msg, NL80211_PMSR_RESP_ATTR_HOST_TIME, host_time_ns) ||
		nla_put_u64(msg, NL80211_PMSR_RESP_ATTR_AP_TSF, ap_tsf_us) ||
		nla_put_flag(msg, NL80211_PMSR_RESP_ATTR_FINAL)) {
		w_logf(ctx, LOG_ERR, "%s: Failed to fill a payload\n", __func__);
		return -ENOMEM;
	}

	data = nla_nest_start(msg, NL80211_PMSR_RESP_ATTR_DATA);
	if (!data)
		return -ENOMEM;

	err = send_pmsr_result_ftm(msg, &req->ftm, ctx, sender, receiver);

	nla_nest_end(msg, data);
	nla_nest_end(msg, resp);
	nla_nest_end(msg, peer);

	return err;
}

static void send_pmsr_result(struct pmsr_request* request, struct wmediumd *ctx,
			     struct station *sender, struct client *client)
{
	struct nl_msg *msg;
	struct nlattr *pmsr, *peers;
	struct pmsr_request_peer *peer;
	int cnt;
	int err;

	msg = nlmsg_alloc();
	if (!msg) {
		w_logf(ctx, LOG_ERR, "%s: nlmsg_alloc failed\n", __func__);
		return;
	}

	if (!genlmsg_put(msg, NL_AUTO_PID, NL_AUTO_SEQ, ctx->family_id,
			 0, NLM_F_REQUEST, HWSIM_CMD_REPORT_PMSR, VERSION_NR)) {
		w_logf(ctx, LOG_ERR, "%s: genlmsg_put failed\n", __func__);
		goto out;
	}

	err = nla_put(msg, HWSIM_ATTR_ADDR_TRANSMITTER,
		      ETH_ALEN, sender->hwaddr);

	pmsr = nla_nest_start(msg, HWSIM_ATTR_PMSR_RESULT);
	if (!pmsr)
		goto out;

	peers = nla_nest_start(msg, NL80211_PMSR_ATTR_PEERS);
	if (!peers)
		goto out;

	list_for_each_entry(peer, &request->peers, list) {
		err = send_pmsr_result_peer(msg, peer, ctx, sender);
		if (err) {
			w_logf(ctx, LOG_ERR,
			       "%s: Failed to send pmsr result from " MAC_FMT \
			       " to " MAC_FMT ". Stopping\n", __func__,
			       MAC_ARGS(sender->addr),
			       MAC_ARGS(peer->addr));
			break;
		}
	}

	nla_nest_end(msg, peers);
	nla_nest_end(msg, pmsr);

	wmediumd_send_to_client(ctx, client, msg);

out:
	nlmsg_free(msg);
}

static void process_start_pmsr(struct nlattr *attrs[], struct wmediumd *ctx,
			       struct client *client)
{
	u8 *hwaddr;
	struct station *sender;

	struct pmsr_request request;
	struct pmsr_request_peer *peer, *tmp;
	int err;

	if (!attrs[HWSIM_ATTR_ADDR_TRANSMITTER]) {
		w_logf(ctx, LOG_ERR, "%s: Missing sender information\n",
		       __func__);
	}

	hwaddr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_TRANSMITTER]);
	sender = get_station_by_addr(ctx, hwaddr);
	if (!sender) {
		w_logf(ctx, LOG_ERR, "%s: Unknown sender " MAC_FMT "\n",
		       __func__, MAC_ARGS(hwaddr));
		return;
	}

	err = parse_pmsr_request(attrs[HWSIM_ATTR_PMSR_REQUEST], &request);
	if (err)
		goto out;

	send_pmsr_result(&request, ctx, sender, client);

out:
	list_for_each_entry_safe(peer, tmp, &request.peers, list) {
		list_del(&peer->list);
		free(peer);
	}
}

/*
 * Handle events from the kernel.  Process CMD_FRAME events and queue them
 * for later delivery with the scheduler.
 */
static void _process_messages(struct nl_msg *msg,
			      struct wmediumd *ctx,
			      struct client *client)
{
	struct nlattr *attrs[HWSIM_ATTR_MAX+1];
	/* netlink header */
	struct nlmsghdr *nlh = nlmsg_hdr(msg);
	/* generic netlink header*/
	struct genlmsghdr *gnlh = nlmsg_data(nlh);

	struct station *sender;
	struct frame *frame;
	struct ieee80211_hdr *hdr;
	u8 *src, *hwaddr, *addr;
	void *new_addrs;
	unsigned int i;

	genlmsg_parse(nlh, 0, attrs, HWSIM_ATTR_MAX, NULL);

	switch (gnlh->cmd) {
	case HWSIM_CMD_FRAME:
		if (attrs[HWSIM_ATTR_ADDR_TRANSMITTER]) {
			hwaddr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_TRANSMITTER]);

			unsigned int data_len =
				nla_len(attrs[HWSIM_ATTR_FRAME]);
			char *data = (char *)nla_data(attrs[HWSIM_ATTR_FRAME]);
			unsigned int flags =
				nla_get_u32(attrs[HWSIM_ATTR_FLAGS]);
			unsigned int tx_rates_len =
				nla_len(attrs[HWSIM_ATTR_TX_INFO]);
			struct hwsim_tx_rate *tx_rates =
				(struct hwsim_tx_rate *)
				nla_data(attrs[HWSIM_ATTR_TX_INFO]);
			u64 cookie = nla_get_u64(attrs[HWSIM_ATTR_COOKIE]);
			u32 freq;

			freq = attrs[HWSIM_ATTR_FREQ] ?
				nla_get_u32(attrs[HWSIM_ATTR_FREQ]) : 2412;

			hdr = (struct ieee80211_hdr *)data;
			src = hdr->addr2;

			if (data_len < 6 + 6 + 4)
				return;

			sender = get_station_by_addr(ctx, hwaddr);
			if (!sender) {
				sender = get_station_by_used_addr(ctx, src);
				if (!sender) {
					w_flogf(ctx, LOG_ERR, stderr,
						"Unable to find sender station by src=" MAC_FMT " nor hwaddr=" MAC_FMT "\n",
						MAC_ARGS(src), MAC_ARGS(hwaddr));
					return;
				}
				memcpy(sender->hwaddr, hwaddr, ETH_ALEN);
			}

			if (!sender->client)
				sender->client = client;

			frame = calloc(1, sizeof(*frame) + data_len);
			if (!frame)
				return;

			memcpy(frame->data, data, data_len);
			frame->data_len = data_len;
			frame->flags = flags;
			frame->cookie = cookie;
			frame->freq = freq;
			frame->sender = sender;
			frame->tx_rates_count =
				tx_rates_len / sizeof(struct hwsim_tx_rate);
			memcpy(frame->tx_rates, tx_rates,
			       min(tx_rates_len, sizeof(frame->tx_rates)));
			queue_frame(ctx, sender, frame);
		}
		break;
	case HWSIM_CMD_ADD_MAC_ADDR:
		if (!attrs[HWSIM_ATTR_ADDR_TRANSMITTER] ||
		    !attrs[HWSIM_ATTR_ADDR_RECEIVER])
			break;
		hwaddr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_TRANSMITTER]);
		addr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_RECEIVER]);
		sender = get_station_by_addr(ctx, hwaddr);
		if (!sender)
			break;
		for (i = 0; i < sender->n_addrs; i++) {
			if (memcmp(sender->addrs[i].addr, addr, ETH_ALEN) == 0) {
				sender->addrs[i].count += 1;
				return;
			}
		}
		new_addrs = realloc(sender->addrs, sizeof(struct addr) * (sender->n_addrs + 1));
		if (!new_addrs)
			break;
		sender->addrs = new_addrs;
		sender->addrs[sender->n_addrs].count = 1;
		memcpy(sender->addrs[sender->n_addrs].addr, addr, ETH_ALEN);
		sender->n_addrs += 1;
		break;
	case HWSIM_CMD_DEL_MAC_ADDR:
		if (!attrs[HWSIM_ATTR_ADDR_TRANSMITTER] ||
		    !attrs[HWSIM_ATTR_ADDR_RECEIVER])
			break;
		hwaddr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_TRANSMITTER]);
		addr = (u8 *)nla_data(attrs[HWSIM_ATTR_ADDR_RECEIVER]);
		sender = get_station_by_addr(ctx, hwaddr);
		if (!sender)
			break;
		for (i = 0; i < sender->n_addrs; i++) {
			if (memcmp(sender->addrs[i].addr, addr, ETH_ALEN))
				continue;
			sender->addrs[i].count -= 1;
			if (sender->addrs[i].count <= 0) {
				sender->n_addrs -= 1;
				memmove(&sender->addrs[i],
					&sender->addrs[sender->n_addrs],
					sizeof(struct addr));
			}
			break;
		}
		break;
	case HWSIM_CMD_START_PMSR:
		process_start_pmsr(attrs, ctx, client);
		break;

	case HWSIM_CMD_ABORT_PMSR:
		// Do nothing. Too late to abort any PMSR.
		break;
	}
}

static int process_messages_cb(struct nl_msg *msg, void *arg)
{
	struct wmediumd *ctx = arg;

	_process_messages(msg, ctx, &ctx->nl_client);
	return 0;
}

static void wmediumd_vu_connected(struct usfstl_vhost_user_dev *dev)
{
	struct wmediumd *ctx = dev->server->data;
	struct client *client;

	client = calloc(1, sizeof(*client));
	dev->data = client;
	client->type = CLIENT_VHOST_USER;
	client->dev = dev;
	list_add(&client->list, &ctx->clients);
}

static void wmediumd_vu_handle(struct usfstl_vhost_user_dev *dev,
			       struct usfstl_vhost_user_buf *buf,
			       unsigned int vring)
{
	struct nl_msg *nlmsg;
	char data[4096];
	size_t len;

	len = iov_read(data, sizeof(data), buf->out_sg, buf->n_out_sg);

	if (!nlmsg_ok((const struct nlmsghdr *)data, len))
		return;
	nlmsg = nlmsg_convert((struct nlmsghdr *)data);
	if (!nlmsg)
		return;

	_process_messages(nlmsg, dev->server->data, dev->data);

	nlmsg_free(nlmsg);
}

static void wmediumd_vu_disconnected(struct usfstl_vhost_user_dev *dev)
{
	struct client *client = dev->data;

	dev->data = NULL;
	wmediumd_remove_client(dev->server->data, client);
}

static int process_set_snr_message(struct wmediumd *ctx, struct wmediumd_set_snr *set_snr) {
	struct station *node1 = get_station_by_addr(ctx, set_snr->node1_mac);
	struct station *node2 = get_station_by_addr(ctx, set_snr->node2_mac);

	if (node1 == NULL || node2 == NULL) {
		return -1;
	}

	ctx->snr_matrix[ctx->num_stas * node2->index + node1->index] = set_snr->snr;
	ctx->snr_matrix[ctx->num_stas * node1->index + node2->index] = set_snr->snr;

	return 0;
}

static int process_load_config_message(struct wmediumd *ctx,
					 struct wmediumd_load_config *reload_config) {
	char *config_path;
	int result = 0;

	config_path = reload_config->config_path;

	if (validate_config(config_path)) {
		clear_config(ctx);
		load_config(ctx, config_path, NULL);
	} else {
		result = -1;
	}

	return result;
}

static int process_reload_current_config_message(struct wmediumd *ctx) {
	char *config_path;
	int result = 0;

	config_path = strdup(ctx->config_path);

	if (validate_config(config_path)) {
		clear_config(ctx);
		load_config(ctx, config_path, NULL);
	} else {
		result = -1;
	}

	free(config_path);

	return result;
}

static int process_get_stations_message(struct wmediumd *ctx, ssize_t *response_len, unsigned char **response_data) {
	struct station *station;
	int station_count = 0;
	int extra_data_len = 0;

	// *reponse_data contains struct wmediumd_station_infos
	// and then lci and civiclocs for each station follows afterwards.
	list_for_each_entry(station, &ctx->stations, list) {
		if (station->client != NULL) {
			++station_count;
			extra_data_len += strlen_safe(station->lci) + 1;
			extra_data_len += strlen_safe(station->civicloc) + 1;
		}
	}

	int station_len = sizeof(uint32_t) + sizeof(struct wmediumd_station_info) * station_count;
	*response_len = station_len + extra_data_len;
	*response_data = malloc(*response_len);

	if (*response_data == NULL) {
		w_logf(ctx, LOG_ERR, "%s: failed allocate response data\n", __func__);
		return -1;
	}

	struct wmediumd_station_infos *station_infos = (struct wmediumd_station_infos *)*response_data;
	station_infos->count = station_count;
	int station_index = 0;
	// Pointer to the memory after structs wmediumd_station_infos
	// to write lci and civicloc for each station.
	char *extra_data_cursor = (char *)&(station_infos->stations[station_count]);

	list_for_each_entry(station, &ctx->stations, list) {
		if (station->client != NULL) {
			struct wmediumd_station_info *station_info = &station_infos->stations[station_index];
			memcpy(station_info->addr, station->addr, ETH_ALEN);
			memcpy(station_info->hwaddr, station->hwaddr, ETH_ALEN);

			station_info->x = station->x;
			station_info->y = station->y;
			station_info->lci_offset = extra_data_cursor - (char *)station_info;
			extra_data_cursor = stpcpy_safe(extra_data_cursor, station->lci) + 1;
			station_info->civicloc_offset = extra_data_cursor - (char *)station_info;
			extra_data_cursor = stpcpy_safe(extra_data_cursor, station->civicloc) + 1;
			station_info->tx_power = station->tx_power;
			station_index++;
		}
	}

	return 0;
}

static int process_set_position_message(struct wmediumd *ctx, struct wmediumd_set_position *set_position) {
	struct station *node = get_station_by_addr(ctx, set_position->mac);

	if (node == NULL) {
		return -1;
	}

	node->x = set_position->x;
	node->y = set_position->y;

	calc_path_loss(ctx);

	return 0;
}

static int process_set_tx_power_message(struct wmediumd *ctx, struct wmediumd_set_tx_power *set_tx_power) {
	struct station *node = get_station_by_addr(ctx, set_tx_power->mac);

	if (node == NULL) {
		return -1;
	}

	node->tx_power = set_tx_power->tx_power;

	calc_path_loss(ctx);

	return 0;
}

static int process_set_lci_message(struct wmediumd *ctx, struct wmediumd_set_lci *set_lci, size_t data_len) {
	struct station *node = get_station_by_addr(ctx, set_lci->mac);

	if (node == NULL) {
		return -1;
	}
	int expected_len = data_len - offsetof(struct wmediumd_set_lci, lci) - 1;
	if (set_lci->lci[expected_len] != '\0') {
		return -1;
	}

	if (node->lci) {
		free(node->lci);
	}
	node->lci = strdup(set_lci->lci);

	return node->lci == NULL ? -1 : 0;
}

static int process_set_civicloc_message(struct wmediumd *ctx, struct wmediumd_set_civicloc *set_civicloc, size_t data_len) {
	struct station *node = get_station_by_addr(ctx, set_civicloc->mac);

	if (node == NULL) {
		return -1;
	}
	int expected_len = data_len - offsetof(struct wmediumd_set_civicloc, civicloc) - 1;
	if (set_civicloc->civicloc[expected_len] != '\0') {
		return -1;
	}

	if (node->civicloc) {
		free(node->civicloc);
	}
	node->civicloc = strdup(set_civicloc->civicloc);

	return node->civicloc == NULL ? -1 : 0;
}

static const struct usfstl_vhost_user_ops wmediumd_vu_ops = {
	.connected = wmediumd_vu_connected,
	.handle = wmediumd_vu_handle,
	.disconnected = wmediumd_vu_disconnected,
};

static void close_pcapng(struct wmediumd *ctx) {
	if (ctx->pcap_file == NULL) {
		return;
	}

	fflush(ctx->pcap_file);
	fclose(ctx->pcap_file);

	ctx->pcap_file = NULL;
}

static void init_pcapng(struct wmediumd *ctx, const char *filename);

static void wmediumd_send_grpc_response(int msq_id, long msg_type_response, enum wmediumd_grpc_response_data_type data_type, ssize_t data_size, unsigned char *data_payload) {
	struct wmediumd_grpc_response_message response_message;
	response_message.msg_type_response = msg_type_response;
	response_message.data_type = data_type;
	response_message.data_size = data_size;
	if (data_size > 0) {
		memcpy(response_message.data_payload, data_payload, data_size);
	}
	msgsnd(msq_id, &response_message, MSG_TYPE_RESPONSE_SIZE, 0);
}

static void wmediumd_grpc_service_handler(struct usfstl_loop_entry *entry) {
	struct wmediumd *ctx = entry->data;
	ssize_t msg_len;

	// Receive event from wmediumd_server
	uint64_t evt;
	read(entry->fd, &evt, sizeof(evt));

	struct wmediumd_grpc_request_message request_message;
	msg_len = msgrcv(ctx->msq_id, &request_message, MSG_TYPE_REQUEST_SIZE, MSG_TYPE_REQUEST, IPC_NOWAIT);
	if (msg_len != MSG_TYPE_REQUEST_SIZE) {
		w_logf(ctx, LOG_ERR, "%s: failed to get request message\n", __func__);
	} else {
		switch (request_message.data_type) {
		case REQUEST_LIST_STATIONS: {
			ssize_t size = 0;
			unsigned char *payload = NULL;
			if (process_get_stations_message(ctx, &size, &payload) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute list_stations\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
			} else {
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK_LIST_STATIONS, size, payload);
			}
			break;
		}
		case REQUEST_LOAD_CONFIG:
			if (process_load_config_message(ctx, (struct wmediumd_load_config *)(request_message.data_payload)) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute load_config\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_RELOAD_CONFIG:
			if (process_reload_current_config_message(ctx) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute reload_config\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_SET_CIVICLOC:
			if (process_set_civicloc_message(ctx, (struct wmediumd_set_civicloc *)(request_message.data_payload), request_message.data_size) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute set_civicloc\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_SET_LCI:
			if (process_set_lci_message(ctx, (struct wmediumd_set_lci *)(request_message.data_payload), request_message.data_size) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute set_lci\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_SET_POSITION:
			if (process_set_position_message(ctx, (struct wmediumd_set_position *)(request_message.data_payload)) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute set_position\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_SET_SNR:
			if (process_set_snr_message(ctx, (struct wmediumd_set_snr *)(request_message.data_payload)) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute set_snr\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_SET_TX_POWER:
			if (process_set_tx_power_message(ctx, (struct wmediumd_set_tx_power *)(request_message.data_payload)) < 0) {
				w_logf(ctx, LOG_ERR, "%s: failed to execute set_tx_power\n", __func__);
				wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
				break;
			}
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_START_PCAP:
			init_pcapng(ctx, ((struct wmediumd_start_pcap *)(request_message.data_payload))->pcap_path);
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		case REQUEST_STOP_PCAP:
			close_pcapng(ctx);
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_ACK, 0, NULL);
			break;
		default:
			w_logf(ctx, LOG_ERR, "%s: unknown request type\n", __func__);
			wmediumd_send_grpc_response(ctx->msq_id, request_message.msg_type_response, RESPONSE_INVALID, 0, NULL);
			break;
		}
	}
	return;
}

// TODO(273384914): Deprecate messages used in wmediumd_control after
// implementing in wmediumd_grpc_service_handler to be used in the command
// 'cvd env'.
static void wmediumd_api_handler(struct usfstl_loop_entry *entry)
{
	struct client *client = container_of(entry, struct client, loop);
	struct wmediumd *ctx = entry->data;
	struct wmediumd_message_header hdr;
	enum wmediumd_message response = WMEDIUMD_MSG_ACK;
	struct wmediumd_message_control control = {};
	struct nl_msg *nlmsg;
	unsigned char *data;
	ssize_t response_len = 0;
	unsigned char *response_data = NULL;
	ssize_t len;

	len = read(entry->fd, &hdr, sizeof(hdr));
	if (len != sizeof(hdr)) {
		if (len > 0) {
			// Skipping log if the fd is closed.
			w_logf(ctx, LOG_ERR, "%s: failed to read header\n", __func__);
		}
		goto disconnect;
	}

	/* safety valve */
	if (hdr.data_len > 1024 * 1024) {
		w_logf(ctx, LOG_ERR, "%s: too large data\n", __func__);
		goto disconnect;
	}

	data = malloc(hdr.data_len);
	if (!data) {
		w_logf(ctx, LOG_ERR, "%s: failed to malloc\n", __func__);
		goto disconnect;
	}

	len = read(entry->fd, data, hdr.data_len);
	if (len != hdr.data_len) {
		w_logf(ctx, LOG_ERR, "%s: failed to read data\n", __func__);
		goto disconnect;
	}

	switch (hdr.type) {
	case WMEDIUMD_MSG_REGISTER:
		if (!list_empty(&client->list)) {
			response = WMEDIUMD_MSG_INVALID;
			break;
		}
		list_add(&client->list, &ctx->clients);
		break;
	case WMEDIUMD_MSG_UNREGISTER:
		if (list_empty(&client->list)) {
			response = WMEDIUMD_MSG_INVALID;
			break;
		}
		list_del_init(&client->list);
		break;
	case WMEDIUMD_MSG_NETLINK:
		if (ctx->ctrl)
			usfstl_sched_ctrl_sync_from(ctx->ctrl);

		if (!nlmsg_ok((const struct nlmsghdr *)data, len)) {
			response = WMEDIUMD_MSG_INVALID;
			break;
		}

		nlmsg = nlmsg_convert((struct nlmsghdr *)data);
		if (!nlmsg)
			break;

		_process_messages(nlmsg, ctx, client);

		nlmsg_free(nlmsg);
		break;
	case WMEDIUMD_MSG_SET_CONTROL:
		/* copy what we get and understand, leave the rest zeroed */
		memcpy(&control, data,
		       min(sizeof(control), hdr.data_len));

		if (client->flags & WMEDIUMD_CTL_NOTIFY_TX_START)
			ctx->need_start_notify--;
		if (control.flags & WMEDIUMD_CTL_NOTIFY_TX_START)
			ctx->need_start_notify++;

		client->flags = control.flags;
		break;
	case WMEDIUMD_MSG_GET_STATIONS:
		if (process_get_stations_message(ctx, &response_len, &response_data) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		response = WMEDIUMD_MSG_STATIONS_LIST;
		break;
	case WMEDIUMD_MSG_SET_SNR:
		if (process_set_snr_message(ctx, (struct wmediumd_set_snr *)data) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_RELOAD_CONFIG:
		if (process_load_config_message(ctx,
				(struct wmediumd_load_config *)data) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_RELOAD_CURRENT_CONFIG:
		if (process_reload_current_config_message(ctx) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_START_PCAP:
		init_pcapng(ctx, ((struct wmediumd_start_pcap *)data)->pcap_path);
		break;
	case WMEDIUMD_MSG_STOP_PCAP:
		close_pcapng(ctx);
		break;
	case WMEDIUMD_MSG_SET_POSITION:
		if (process_set_position_message(ctx, (struct wmediumd_set_position *)data) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_SET_LCI:
		if (process_set_lci_message(ctx,
				(struct wmediumd_set_lci *)data,
				hdr.data_len) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_SET_CIVICLOC:
		if (process_set_civicloc_message(ctx,
				(struct wmediumd_set_civicloc *)data,
				hdr.data_len) < 0) {
			response = WMEDIUMD_MSG_INVALID;
		}
		break;
	case WMEDIUMD_MSG_ACK:
		assert(client->wait_for_ack == true);
		assert(hdr.data_len == 0);
		client->wait_for_ack = false;
		/* don't send a response to a response, of course */
		return;
	default:
		w_logf(ctx, LOG_ERR, "%s: unknown message\n", __func__);
		response = WMEDIUMD_MSG_INVALID;
		break;
	}

	/* return a response */
	hdr.type = response;
	hdr.data_len = response_len;
	len = write(entry->fd, &hdr, sizeof(hdr));
	if (len != sizeof(hdr)) {
		w_logf(ctx, LOG_ERR, "%s: failed to write response header\n", __func__);
		goto disconnect;
	}

	if (response_data != NULL) {
		if (response_len != 0) {
			len = write(entry->fd, response_data, response_len);

			if (len != response_len) {
				free(response_data);
				w_logf(ctx, LOG_ERR, "%s: failed to write response data\n", __func__);
				goto disconnect;
			}
		}

		free(response_data);
		response_data = NULL;
	}

	return;
disconnect:
	usfstl_loop_unregister(&client->loop);
	wmediumd_remove_client(ctx, client);
}

static void wmediumd_api_connected(int fd, void *data)
{
	struct wmediumd *ctx = data;
	struct client *client;

	client = calloc(1, sizeof(*client));
	client->type = CLIENT_API_SOCK;
	client->loop.fd = fd;
	client->loop.data = ctx;
	client->loop.handler = wmediumd_api_handler;
	usfstl_loop_register(&client->loop);
	INIT_LIST_HEAD(&client->list);
}

/*
 * Register with the kernel to start receiving new frames.
 */
static int send_register_msg(struct wmediumd *ctx)
{
	struct nl_sock *sock = ctx->sock;
	struct nl_msg *msg;
	int ret;

	msg = nlmsg_alloc();
	if (!msg) {
		w_logf(ctx, LOG_ERR, "Error allocating new message MSG!\n");
		return -1;
	}

	if (genlmsg_put(msg, NL_AUTO_PID, NL_AUTO_SEQ, ctx->family_id,
			0, NLM_F_REQUEST, HWSIM_CMD_REGISTER,
			VERSION_NR) == NULL) {
		w_logf(ctx, LOG_ERR, "%s: genlmsg_put failed\n", __func__);
		ret = -1;
		goto out;
	}

	ret = nl_send_auto_complete(sock, msg);
	if (ret < 0) {
		w_logf(ctx, LOG_ERR, "%s: nl_send_auto failed\n", __func__);
		ret = -1;
		goto out;
	}
	ret = 0;

out:
	nlmsg_free(msg);
	return ret;
}

static void sock_event_cb(struct usfstl_loop_entry *entry)
{
	struct wmediumd *ctx = entry->data;

	nl_recvmsgs_default(ctx->sock);
}

/*
 * Setup netlink socket and callbacks.
 */
static int init_netlink(struct wmediumd *ctx)
{
	struct nl_sock *sock;
	int ret;

	ctx->cb = nl_cb_alloc(NL_CB_CUSTOM);
	if (!ctx->cb) {
		w_logf(ctx, LOG_ERR, "Error allocating netlink callbacks\n");
		return -1;
	}

	sock = nl_socket_alloc_cb(ctx->cb);
	if (!sock) {
		w_logf(ctx, LOG_ERR, "Error allocating netlink socket\n");
		return -1;
	}

	ctx->sock = sock;

	ret = genl_connect(sock);
	if (ret < 0) {
		w_logf(ctx, LOG_ERR, "Error connecting netlink socket ret=%d\n", ret);
		return -1;
	}

	ctx->family_id = genl_ctrl_resolve(sock, "MAC80211_HWSIM");
	if (ctx->family_id < 0) {
		w_logf(ctx, LOG_ERR, "Family MAC80211_HWSIM not registered\n");
		return -1;
	}

	nl_cb_set(ctx->cb, NL_CB_MSG_IN, NL_CB_CUSTOM, process_messages_cb, ctx);
	nl_cb_err(ctx->cb, NL_CB_CUSTOM, nl_err_cb, ctx);

	return 0;
}

/*
 *	Print the CLI help
 */
static void print_help(int exval)
{
	printf("wmediumd v%s - a wireless medium simulator\n", VERSION_STR);
	printf("wmediumd [-h] [-V] [-l LOG_LVL] [-x FILE] -c FILE \n\n");

	printf("  -h              print this help and exit\n");
	printf("  -V              print version and exit\n\n");

	printf("  -l LOG_LVL      set the logging level\n");
	printf("                  LOG_LVL: RFC 5424 severity, values 0 - 7\n");
	printf("                  >= 3: errors are logged\n");
	printf("                  >= 5: startup msgs are logged\n");
	printf("                  >= 6: dropped packets are logged (default)\n");
	printf("                  == 7: all packets will be logged\n");
	printf("  -c FILE         set input config file\n");
	printf("  -x FILE         set input PER file\n");
	printf("  -t socket       set the time control socket\n");
	printf("  -u socket       expose vhost-user socket, don't use netlink\n");
	printf("  -a socket       expose wmediumd API socket\n");
	printf("  -n              force netlink use even with vhost-user\n");
	printf("  -p FILE         log packets to pcapng file FILE\n");

	exit(exval);
}

static void init_pcapng(struct wmediumd *ctx, const char *filename)
{
	struct {
		uint32_t type, blocklen, byte_order;
		uint16_t ver_maj, ver_min;
		uint64_t seclen;
		uint32_t blocklen2;
	} __attribute__((packed)) blockhdr = {
		.type = 0x0A0D0D0A,
		.blocklen = sizeof(blockhdr),
		.byte_order = 0x1A2B3C4D,
		.ver_maj = 1,
		.ver_min = 0,
		.seclen = -1,
		.blocklen2 = sizeof(blockhdr),
	};
	struct {
		uint32_t type, blocklen;
		uint16_t linktype, reserved;
		uint32_t snaplen;
		struct {
			uint16_t code, len;
			uint8_t val, pad[3];
		} opt_if_tsresol;
		struct {
			uint16_t code, len;
		} opt_endofopt;
		uint32_t blocklen2;
	} __attribute__((packed)) idb = {
		.type = 1,
		.blocklen = sizeof(idb),
		.linktype = 127, // radiotap
		.snaplen = -1,
		.opt_if_tsresol.code = 9,
		.opt_if_tsresol.len = 1,
		.opt_if_tsresol.val = 6, // usec
		.blocklen2 = sizeof(idb),
	};

	if (ctx->pcap_file != NULL) {
		close_pcapng(ctx);
	}

	if (!filename)
		return;

	ctx->pcap_file = fopen(filename, "w+");
	fwrite(&blockhdr, sizeof(blockhdr), 1, ctx->pcap_file);
	fwrite(&idb, sizeof(idb), 1, ctx->pcap_file);
}

#ifndef VIRTIO_F_VERSION_1
#define VIRTIO_F_VERSION_1 32
#endif

int wmediumd_main(int argc, char *argv[], int event_fd, int msq_id)
{
	int opt;
	struct wmediumd ctx = {};
	char *config_file = NULL;
	char *per_file = NULL;
	const char *time_socket = NULL, *api_socket = NULL;
	struct usfstl_sched_ctrl ctrl = {};
	struct usfstl_vhost_user_server vusrv = {
		.ops = &wmediumd_vu_ops,
		.max_queues = HWSIM_NUM_VQS,
		.input_queues = 1 << HWSIM_VQ_TX,
		.features = 1ULL << VIRTIO_F_VERSION_1,
		.protocol_features =
			1ULL << VHOST_USER_PROTOCOL_F_INBAND_NOTIFICATIONS,
		.data = &ctx,
	};
	bool use_netlink, force_netlink = false;

	setvbuf(stdout, NULL, _IOLBF, BUFSIZ);

	if (argc == 1) {
		fprintf(stderr, "This program needs arguments....\n\n");
		print_help(EXIT_FAILURE);
	}

	ctx.log_lvl = 6;
	unsigned long int parse_log_lvl;
	char* parse_end_token;

	while ((opt = getopt(argc, argv, "hVc:l:x:t:u:a:np:")) != -1) {
		switch (opt) {
		case 'h':
			print_help(EXIT_SUCCESS);
			break;
		case 'V':
			printf("wmediumd v%s - a wireless medium simulator "
			       "for mac80211_hwsim\n", VERSION_STR);
			exit(EXIT_SUCCESS);
			break;
		case 'c':
			config_file = optarg;
			break;
		case 'x':
			printf("Input packet error rate file: %s\n", optarg);
			per_file = optarg;
			break;
		case ':':
			printf("wmediumd: Error - Option `%c' "
			       "needs a value\n\n", optopt);
			print_help(EXIT_FAILURE);
			break;
		case 'l':
			parse_log_lvl = strtoul(optarg, &parse_end_token, 10);
			if ((parse_log_lvl == ULONG_MAX && errno == ERANGE) ||
			     optarg == parse_end_token || parse_log_lvl > 7) {
				printf("wmediumd: Error - Invalid RFC 5424 severity level: "
							   "%s\n\n", optarg);
				print_help(EXIT_FAILURE);
			}
			ctx.log_lvl = parse_log_lvl;
			break;
		case 't':
			time_socket = optarg;
			break;
		case 'u':
			vusrv.socket = optarg;
			break;
		case 'a':
			api_socket = optarg;
			break;
		case 'n':
			force_netlink = true;
			break;
		case 'p':
			init_pcapng(&ctx, optarg);
			break;
		case '?':
			printf("wmediumd: Error - No such option: "
			       "`%c'\n\n", optopt);
			print_help(EXIT_FAILURE);
			break;
		}

	}

	if (optind < argc)
		print_help(EXIT_FAILURE);

	if (!config_file) {
		printf("%s: config file must be supplied\n", argv[0]);
		print_help(EXIT_FAILURE);
	}

	w_logf(&ctx, LOG_NOTICE, "Input configuration file: %s\n", config_file);

	INIT_LIST_HEAD(&ctx.stations);
	INIT_LIST_HEAD(&ctx.clients);
	INIT_LIST_HEAD(&ctx.clients_to_free);

	if (load_config(&ctx, config_file, per_file))
		return EXIT_FAILURE;

	use_netlink = force_netlink || !vusrv.socket;

	/* init netlink */
	if (use_netlink && init_netlink(&ctx) < 0)
		return EXIT_FAILURE;

	if (ctx.intf) {
		ctx.intf_job.start = 10000; // usec
		ctx.intf_job.name = "interference update";
		ctx.intf_job.data = &ctx;
		ctx.intf_job.callback = wmediumd_intf_update;
		usfstl_sched_add_job(&scheduler, &ctx.intf_job);
	}

	if (vusrv.socket)
		usfstl_vhost_user_server_start(&vusrv);

	if (use_netlink) {
		ctx.nl_client.type = CLIENT_NETLINK;
		list_add(&ctx.nl_client.list, &ctx.clients);

		ctx.nl_loop.handler = sock_event_cb;
		ctx.nl_loop.data = &ctx;
		ctx.nl_loop.fd = nl_socket_get_fd(ctx.sock);
		usfstl_loop_register(&ctx.nl_loop);

		/* register for new frames */
		if (send_register_msg(&ctx) == 0)
			w_logf(&ctx, LOG_NOTICE, "REGISTER SENT!\n");
	}

	if (api_socket) {
		signal(SIGPIPE, SIG_IGN);
		usfstl_uds_create(api_socket, wmediumd_api_connected, &ctx);
	}

	if (time_socket) {
		usfstl_sched_ctrl_start(&ctrl, time_socket,
				      1000 /* nsec per usec */,
				      (uint64_t)-1 /* no ID */,
				      &scheduler);
		vusrv.scheduler = &scheduler;
		vusrv.ctrl = &ctrl;
		ctx.ctrl = &ctrl;
	} else {
		usfstl_sched_wallclock_init(&scheduler, 1000);
	}

	// Control event_fd to communicate WmediumdService.
	ctx.grpc_loop.handler = wmediumd_grpc_service_handler;
	ctx.grpc_loop.data = &ctx;
	ctx.grpc_loop.fd = event_fd;
	usfstl_loop_register(&ctx.grpc_loop);
	ctx.msq_id = msq_id;

	while (1) {
		if (time_socket) {
			usfstl_sched_next(&scheduler);
		} else {
			usfstl_sched_wallclock_wait_and_handle(&scheduler);

			if (usfstl_sched_next_pending(&scheduler, NULL))
				usfstl_sched_next(&scheduler);
		}

		while (!list_empty(&ctx.clients_to_free)) {
			struct client *client;

			client = list_first_entry(&ctx.clients_to_free,
						  struct client, list);

			list_del(&client->list);
			free(client);
		}
	}

	free(ctx.sock);
	free(ctx.cb);
	free(ctx.intf);
	free(ctx.per_matrix);

	return EXIT_SUCCESS;
}
