package autotest.moblab.wizard;

import autotest.moblab.MoblabTest;
import autotest.moblab.wizard.WizardCard;

public class ConfigWizardTest extends MoblabTest {

  public void testWizard() {
    ConfigWizard wizard = new ConfigWizard();
    WizardCard[] cards = new WizardCard[] { new WizardCard.StubCard(), new WizardCard.StubCard()};
    wizard.setCards(cards);
  }
}
