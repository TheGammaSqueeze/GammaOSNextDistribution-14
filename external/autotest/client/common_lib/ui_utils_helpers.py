from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import ui_utils
from autotest_lib.client.cros.input_playback import keyboard
import logging
import time


class UIPrinterHelper(object):
    """Helper lib for ChromeOS UI automation."""

    def __init__(self, ui_handler=None, chrome=None):
        """ui_handler or chrome must be provided. ui_handler is an already
        existing ui object."""
        if not ui_handler and chrome:
            self.ui = ui_utils.UI_Handler()
            self.ui.start_ui_root(chrome)
        elif not chrome and ui_handler:
            self.ui = ui_handler
        else:
            raise error.TestError(
                "Either the chrome object or ui_handler must be provided.")
        self._keyboard = None

    def print_to_custom_printer(self, printer_name, isPDF=False):
        """Open the printer menu, select the printer given and click print."""
        self.open_printer_menu()
        self.open_see_more_print_sub_menu()
        self.select_printer_from_see_more_menu(printer_name)
        if not self.check_print_window_open():
            raise error.TestError("Print not open after setting printer.")
        self.click_print(isPDF)
        if self._keyboard:
            self._keyboard.close()

    def check_print_window_open(self):
        """Check if the print window is still open."""
        start_time = time.time()

        # Giving up to 5 seconds for the window to load.
        while time.time() - start_time < 5:
            if (self.ui.item_present('Destination', role='inlineTextBox') and
                    self.ui.item_present('Cancel', role='button')) and not (
                    self.ui.item_present('Loading preview')):
                return True

        logging.info("Print Window was not open {}"
                     .format(self.ui.get_name_role_list()))
        self.ui.screenshoter.take_ss()
        return False

    def open_printer_menu(self, retry=0):
        """Open the printer menu via the Chrome Dropdown."""
        if not self._is_chrome_menu_open():
            self.ui.doDefault_on_obj('Chrome')
        self.ui.wait_for_ui_obj('/Print/', role='menuItem', isRegex=True)
        self.ui.doDefault_on_obj('/Print/', isRegex=True)
        if retry == 0:
            try:
                self.wait_for_print_ready()
            except error.TestError:
                self.open_printer_menu(retry=1)
        else:
            self.wait_for_print_ready()

    def wait_for_print_ready(self):
        """Wait for the print page to fully load."""
        try:
            self.ui.wait_for_ui_obj('Fit to width', role='button', timeout=30)
        except:
            logging.info("Missing {} in {}".format("Fit to width",
                         self.ui.get_name_role_list()))
            raise error.TestError("Fit to width no load")
        self.ui.wait_for_ui_obj('Loading preview', remove=True)

    def open_see_more_print_sub_menu(self):
        """Open see more sub menu via the clicking it in the dropdown."""
        if not self._keyboard:
            self._keyboard = keyboard.Keyboard()

        # Some tests have a notification which causes the chrome menu
        # to remain open, and loose focus on the print page.
        if self._is_chrome_menu_open():
            self._keyboard.press_key("escape")
            self.verify_print_menu_open()

        self.ui.wait_for_ui_obj('Destination Save as PDF')
        self.ui.doDefault_on_obj("Destination Save as PDF", role='popUpButton')

        self.ui.wait_for_ui_obj("See more destinations", role='menuItem')
        self.ui.doDefault_on_obj("See more destinations", role='menuItem')
        self.is_see_more_menu_open()

    def _is_chrome_menu_open(self):
        """Return True if the chrome dropdown menu is still open."""
        return self.ui.item_present("/Print/", role="menuItem", isRegex=True)

    def select_printer_from_see_more_menu(self, printer_name):
        """Click a printer from the "see more" sub menu within print page."""
        if not self.is_see_more_menu_open():
            raise error.TestError(
                "Cannot select printer from sub menu as its not open.")
        # Easier to find via regex since the actual cell has the name twice.
        if not self.is_str_regex(printer_name):
            printer_name = self.make_str_regex(printer_name)
        self.ui.wait_for_ui_obj(printer_name, isRegex=True, role='cell')
        self.ui.doDefault_on_obj(printer_name, isRegex=True, role='cell')
        self.ui.wait_for_ui_obj(printer_name, role='cell', remove=True, isRegex=True)
        # Wait for the "Setting up " loading icon to finish
        self.ui.wait_for_ui_obj('Setting up ', remove=True)

    def click_print(self, isPDF=False):
        """Click the print button. Click save if PDF."""
        self.verify_print_menu_open()
        self.ui.doDefault_on_obj('Save' if isPDF else 'Print', role='button')
        if isPDF:
            pass  # TODO implement the save menu

    def is_see_more_menu_open(self):
        """Return True if the print menu is open."""
        try:
            self.ui.wait_for_ui_obj("Select a destination")
        except error.TestError:
            return False
        return True

    def is_print_menu_open(self):
        """Return True if the print menu is open."""
        return self.ui.item_present("Print", role="window")

    def verify_print_menu_open(self):
        """Verify print menu is open. If not raise TestError."""
        if not self.is_print_menu_open():
            raise error.TestError(
                "Cannot open See more print menu when print screen not open")

    def is_str_regex(self, var):
        """Return True if the given var starts and ends with "/"."""
        if len(var) < 1:
            return False
        if var[0] == "/" and var[-1] == "/":
            return True
        return False

    def make_str_regex(self, var):
        """Return the provided var with a leading and ending "/"."""
        return ("/" + var + "/")
