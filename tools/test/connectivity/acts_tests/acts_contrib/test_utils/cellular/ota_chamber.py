# Copyright 2020 Google Inc. All Rights Reserved.
# Author: oelayach@google.com

import importlib
from acts import logger

ACTS_CONTROLLER_CONFIG_NAME = 'Chamber'


def create(configs):
    """Factory method for Signal Generators.

    Args:
        configs: list of dicts with signal generator settings
    """
    SUPPORTED_MODELS = {
        ("Keysight", "CATR"): "lassen.controllers.chamber.KeysightCATRChamber",
        ("Mock", "Chamber"): "lassen.controllers.chamber.MockChamber"
    }
    objs = []
    for config in configs:
        if (config["brand"], config["model"]) not in SUPPORTED_MODELS:
            raise ValueError("Not a valid chamber model.")
        module = importlib.import_module(SUPPORTED_MODELS[(config["brand"],
                                                           config["model"])])
        chamber = module.Chamber(config)
        objs.append(chamber)
    return objs


def destroy(devices):
    for device in devices:
        device.reset()


class Chamber():
    """Base class implementation for signal generators.

    Base class provides functions whose implementation is shared by all
    chambers.
    """

    def __init__(self, config):
        self.config = config
        self.log = logger.create_tagged_trace_logger("{}{}".format(
            self.config['brand'], self.config['model']))
        self.id_check(self.config)
        self.reset()

    def id_check(self, config):
        """ Resets signal generator."""
        self.log.info("ID Check Successful.")

    def reset(self):
        """ Resets signal generator."""
        self.log.info("Resetting instrument.")

    def disconnect(self):
        """ Disconnects Chamber."""
        self.log.info("Disconnecting instrument.")

    def get_phi(self):
        return self.phi

    def get_theta(self):
        return self.phi

    def move_phi_abs(self, phi):
        self.log.info("Moving to Phi={}".format(phi))
        self.phi = phi

    def move_theta_abs(self, theta):
        self.log.info("Moving to Theta={}".format(theta))
        self.theta = theta

    def move_phi_rel(self, phi):
        self.log.info("Moving Phi by {} degrees".format(phi))
        self.phi = self.phi + phi

    def move_theta_rel(self, theta):
        self.log.info("Moving Theta by {} degrees".format(theta))
        self.theta = self.theta + theta

    def reset_phi(self):
        self.log.info("Resetting Phi.")
        self.phi = 0

    def reset_theta(self):
        self.log.info("Resetting Theta.")
        self.theta = 0

    def reset_phi_theta(self):
        """ Resets signal generator."""
        self.log.info("Resetting to home.")
