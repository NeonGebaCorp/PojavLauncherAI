package git.artdeell.installer_agent;

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowEvent;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.AbstractButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;

public class InstallerAgent implements AWTEventListener {
    // Constants
    private static final int WINDOW_EVENT_MASK = AWTEvent.WINDOW_EVENT_MASK;
    private static final int COMPONENT_TIMEOUT = 30000;

    // Fields
    private boolean forgeWindowHandled;
    private final boolean suppressProfileCreation;
    private final boolean optiFineInstallation;
    private final String modpackFixupId;
    private final Timer componentTimer;

    public InstallerAgent(boolean noProfileSuppression, boolean optifine, String modpackFixupId) {
        this.suppressProfileCreation = noProfileSuppression;
        this.optiFineInstallation = optifine;
        this.modpackFixupId = modpackFixupId;
        this.forgeWindowHandled = false;
        this.componentTimer = new Timer();
    }

    @Override
    public void eventDispatched(AWTEvent event) {
        WindowEvent windowEvent = (WindowEvent) event;
        Window window = windowEvent.getWindow();
        if (windowEvent.getID() != WindowEvent.WINDOW_OPENED) {
            return;
        }
        if (forgeWindowHandled && window instanceof JDialog) { // expecting a new dialog
            handleDialog(window);
            return;
        }
        if (!forgeWindowHandled) { // false at startup, so we will handle the first window as the Forge one
            forgeWindowHandled = handleMainWindow(window);
            checkComponentTimer();
        }
    }

    public void checkComponentTimer() {
        if (forgeWindowHandled) {
            componentTimer.cancel();
            componentTimer.purge();
            return;
        }
        componentTimer.schedule(new ComponentTimeoutTask(), COMPONENT_TIMEOUT);
    }

    public boolean handleMainWindow(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new MainWindowFilter());
        AbstractButton okButton = null;
        for (Component component : components) {
            if (component instanceof AbstractButton) {
                AbstractButton abstractButton = (AbstractButton) component;
                abstractButton = optiFineInstallation ? handleOptiFineButton(abstractButton) : handleForgeButton(abstractButton);
                if (abstractButton != null) {
                    okButton = abstractButton;
                }
            }
        }
        if (okButton == null) {
            System.out.println("Failed to set all the UI components, will try again in the next window");
            return false;
        } else {
            ProfileFixer.storeProfile(optiFineInstallation ? "OptiFine" : "forge");
            EventQueue.invokeLater(() -> okButton.doClick()); // do that after forge actually builds its window, otherwise we set the path too fast
            return true;
        }
    }

    public AbstractButton handleForgeButton(AbstractButton abstractButton) {
        if (abstractButton == null) {
            return null;
        }
        String text = abstractButton.getText();
        if ("OK".equals(text)) {
            return abstractButton; // return the button, so we can press it after processing other stuff
        }
        if ("Install client".equals(text)) {
            abstractButton.doClick(); // It should be the default, but let's make sure
        }
        return null;
    }

    public AbstractButton handleOptiFineButton(AbstractButton abstractButton) {
        if (abstractButton == null) {
            return null;
        }
        String text = abstractButton.getText();
        if ("Install".equals(text)) {
            return abstractButton;
        }
        return null;
    }

    public void handleDialog(Window window) {
        List<Component> components = new ArrayList<>();
        insertAllComponents(components, window, new DialogFilter()); // ensure that it's a JOptionPane dialog
        if (components.size() == 1) {
            // another common trait of them - they only have one option pane in them,
            // so we can discard the rest of the dialog structure
            // also allows us to discard dialogs with progress bars which older installers use
            JOptionPane optionPane = (JOptionPane) components.get(0);
            if (optionPane == null) {
                return;
            }
            int messageType = optionPane.getMessageType();
            if (messageType == JOptionPane.INFORMATION_MESSAGE) { // forge doesn't emit information messages for other reasons yet
                System.out.println("The install was successful!");
                ProfileFixer.reinsertProfile(optiFineInstallation ? "OptiFine" : "forge", modpackFixupId, suppressProfileCreation);
                System.exit(0); // again, forge doesn't call exit for some reason, so we do that ourselves here
            }
        }
    }

    public void insertAllComponents(List<Component> components, Container parent, ComponentFilter filter) {
        if (parent == null) {
            return;
        }
        int componentCount = parent.getComponentCount();
        for (int i = 0; i < componentCount; i++) {
            Component component = parent.getComponent(i);
            if (component == null) {
                continue;
            }
            if (filter.checkComponent(component)) {
                components.add(component);
            }
            if (component instanceof Container) {
                insertAllComponents(components, (Container) component, filter);
            }
        }
    }

    public static void premain(String args, Instrumentation inst) {
        boolean noProfileSuppression = false;
        boolean optifine = false;
        String modpackFixupId = null;
        if (args != null) {
            modpackFixupId = findQuotedString(args);
            if (modpackFixupId != null) {
                noProfileSuppression = args.contains("NPS") && !modpackFixupId.contains("NPS");
                // No Profile Suppression
                optifine = args.contains("OF") && !modpackFixupId.contains("OF");
                // OptiFine
            } else {
                noProfileSuppression = args.contains("NPS"); // No Profile Suppression
                optifine = args.contains("OF"); // OptiFine
            }
        }
        InstallerAgent agent = new InstallerAgent(noProfileSuppression, optifine, modpackFixupId);
        Toolkit.getDefaultToolkit()
                .addAWTEventListener(agent, WINDOW_EVENT_MASK);
    }

    private static String findQuotedString(String args) {
        int quoteIndex = args.indexOf('"');
        if (quoteIndex == -1) {
            return null;
        }
        int nextQuoteIndex = args.indexOf('"', quoteIndex + 1);
        if (nextQuoteIndex == -1) {
            return null;
        }
        return args.substring(quoteIndex + 1, nextQuoteIndex);
    }

    // Inner class
    private static class ComponentTimeoutTask extends TimerTask {
        public ComponentTimeoutTask() {
            // constructor
        }

        @Override
        public void run() {
            // do something when the timer expires
        }
    }

    // Interface
    private interface ComponentFilter {
        boolean checkComponent(Component component);
    }

    private static class MainWindowFilter implements ComponentFilter {
        @Override
        public boolean checkComponent(Component component) {
            // check if the component is the main window
            return true;
        }
    }

    private static class DialogFilter implements ComponentFilter {
        @Override
        public boolean checkComponent(Component component) {
            // check if the component is a JOptionPane dialog
            return component instanceof JOptionPane;
        }
    }
}
