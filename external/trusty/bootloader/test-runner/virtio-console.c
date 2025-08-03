#include <arch/io-mem.h>
#include <assert.h>
#include <stdbool.h>
#include <test-runner-arch.h>
#include <trusty/sysdeps.h>
#include <utils.h>
#include <virtio-console.h>
#include <virtio-device.h>
#include <virtio.h>

#define DESIRED_FEATURES (VIRTIO_CONSOLE_F_MULTIPORT)

static struct virtio_console console;

static struct virtq cmd_input;
static struct virtq_raw cmd_input_raw;
static struct virtq cmd_output;
static struct virtq_raw cmd_output_raw;

struct console_control {
    uint32_t id;
    uint16_t event;
    uint16_t value;
    char buf[];
};

/*
 * The biggest message we need to read for console control is a console
 * message plus the biggest name we're willing to put on a console.
 */
#define CMSG_IN_MAX (sizeof(struct console_control) + MAX_PORT_NAME_SIZE)
static char cmsg_buf[VQ_SIZE][CMSG_IN_MAX];

static void send_control_msg(const struct console_control* msg) {
    send_vq(&cmd_output, (const void*)msg, sizeof(*msg));
}

/* Check if a vq could give us a buffer now */
static bool vq_quick_ready(struct virtq* vq) {
    if (!vq_ready(vq)) {
        vq_kick(vq);
    }
    return vq_ready(vq);
}

/*
 * Gets the next control message in the channel, returning NULL if none are
 * immediately available.
 *
 * To implement this, we need a slightly different approach than our other
 * queues. We keep a buffer array in the available queue, and check whether
 * queue is ready for retrieving message.
 *
 * Once done reading the message, the caller must call release_control_msg
 * to relinquish ownership of the message before calling get_control_msg
 * again.
 */
static const struct console_control* get_control_msg(size_t* buf_size,
                                                     size_t* msg_idx) {
    uint32_t idx = cmd_input.old_used_idx % cmd_input.num_bufs;

    if (!vq_quick_ready(&cmd_input)) {
        return NULL;
    }

    *buf_size = vq_adv(&cmd_input);
    *msg_idx = idx;

    return (struct console_control*)cmsg_buf[idx];
}

/*
 * Releases ownership of the control_msg from get_control_msg, enabling
 * get_control_msg to be called again.
 */
static void release_control_msg(size_t idx) {
    vq_set_buf_w(&cmd_input, idx, cmsg_buf[idx], CMSG_IN_MAX);
    vq_make_avail(&cmd_input, idx);
}

static void control_setup(struct virtio_config* vio) {
    uint32_t idx = 0;

    vq_init(&cmd_input, &cmd_input_raw, vio, true);
    vq_init(&cmd_output, &cmd_output_raw, vio, false);
    vq_attach(&cmd_input, VIRTIO_CONSOLE_CTRL_RX);
    vq_attach(&cmd_output, VIRTIO_CONSOLE_CTRL_TX);
    for (idx = 0; idx < cmd_input.num_bufs; idx++) {
        vq_set_buf_w(&cmd_input, idx, cmsg_buf[idx], CMSG_IN_MAX);
        vq_make_avail(&cmd_input, idx);
    }
}

static void port_open(struct virtio_console* console, size_t port_id) {
    const struct console_control connect_msg = {
            .event = VIRTIO_CONSOLE_PORT_OPEN,
            .id = port_id,
            .value = 1,
    };
    send_control_msg(&connect_msg);
    console->ports[port_id].guest_connected = true;
}

/*
 * Finds the first queue for a given port.
 * The layout is in0, out0, control_in, control_out, in1, out1, in2, out2, ...
 */
static uint16_t virtio_console_q(size_t port_id) {
    return port_id * 2 + ((port_id >= 1) ? 2 : 0);
}

void virtio_console_connect_port(struct virtio_console* console,
                                 size_t port_id,
                                 struct virtq* vq_in,
                                 struct virtq* vq_out) {
    /* Attach the virtqueues to the relevant queue IDs */
    vq_attach(vq_in, virtio_console_q(port_id) + VIRTIO_CONSOLE_RX_OFFSET);
    vq_attach(vq_out, virtio_console_q(port_id) + VIRTIO_CONSOLE_TX_OFFSET);

    /* Use the control queue to tell the host we are ready */
    port_open(console, port_id);
}

static void port_ready(struct virtio_console* console, size_t port_id) {
    assert(console->ports[port_id].host_connected);
    struct console_control msg_send = {
            .id = port_id,
            .event = VIRTIO_CONSOLE_PORT_READY,
            .value = 1,
    };
    send_control_msg(&msg_send);
}

static void control_scan(struct virtio_console* console) {
    size_t buf_size;
    size_t msg_idx;
    const struct console_control* msg;
    for (size_t i = 0; i < MAX_PORTS; i++) {
        console->ports[i].host_connected = false;
        console->ports[i].guest_connected = false;
        console->ports[i].name[0] = 0;
    }

    const struct console_control dev_ready = {
            .event = VIRTIO_CONSOLE_DEVICE_READY,
            .value = 1,
    };

    send_control_msg(&dev_ready);

    while ((msg = get_control_msg(&buf_size, &msg_idx))) {
        switch (msg->event) {
        case VIRTIO_CONSOLE_DEVICE_ADD:
            console->ports[msg->id].host_connected = true;
            console->ports[msg->id].name[0] = 0;
            /*
             * Must be released before port_ready is called, or QEMU will
             * drop the response packet on the ground.
             */
            release_control_msg(msg_idx);
            port_ready(console, msg->id);
            break;
        case VIRTIO_CONSOLE_DEVICE_REMOVE:
            console->ports[msg->id].host_connected = false;
            release_control_msg(msg_idx);
            break;
        case VIRTIO_CONSOLE_PORT_NAME:
            buf_size = MIN(buf_size, MAX_PORT_NAME_SIZE - 1);
            trusty_memcpy(console->ports[msg->id].name, msg->buf, buf_size);
            console->ports[msg->id].name[buf_size] = 0;
            release_control_msg(msg_idx);
            break;
        case VIRTIO_CONSOLE_DEVICE_READY:
        case VIRTIO_CONSOLE_CONSOLE_PORT:
        case VIRTIO_CONSOLE_RESIZE:
        case VIRTIO_CONSOLE_PORT_OPEN:
        default:
            release_control_msg(msg_idx);
            break;
        }
    }
}

struct virtio_console* init_virtio_console(void) {
    struct virtio_config* console_vio = virtio_probe_console();
    if (!console_vio) {
        /* We didn't find a legacy multiport console */
        return NULL;
    }

    /* Reset device */
    virtio_reset_device(console_vio);

    /* Acknowledge device */
    virtio_or_status(console_vio, VIRTIO_STATUS_ACKNOWLEDGE);

    /* Set driver bit */
    virtio_or_status(console_vio, VIRTIO_STATUS_DRIVER);

    /* Check that our features are available */
    uint64_t features = virtio_get_features(console_vio);
    assert((features & DESIRED_FEATURES) == DESIRED_FEATURES);

    /* Write desired feature bits */
    virtio_set_features(console_vio, DESIRED_FEATURES);

    /* We are done negotiating features */
    virtio_or_status(console_vio, VIRTIO_STATUS_FEATURES_OK);

    /* The device accepted our features */
    assert(virtio_get_status(console_vio) & VIRTIO_STATUS_FEATURES_OK);

    /* Set up control virtqueues */
    virtio_set_guest_page_size(console_vio, PAGE_SIZE);

    control_setup(console_vio);
    console.vio = console_vio;

    /* We are now able to drive the device */
    virtio_or_status(console_vio, VIRTIO_STATUS_DRIVER_OK);

    /* Chat with the control queues to update our ports */
    control_scan(&console);

    return &console;
}
