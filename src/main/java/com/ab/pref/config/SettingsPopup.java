package com.ab.pref.config;

import com.ab.jpref.config.Config;
import com.ab.pref.MainPanel;
import com.ab.pref.config.PConfig.Host;
import com.ab.pref.PUtil;
import com.ab.pref.widgets.ButtonPanel;
import com.ab.pref.widgets.PButton;
import com.ab.jpref.config.I18n;
import static com.ab.jpref.config.I18n.m;
import com.ab.util.Logger;
import com.ab.util.Tuple;
import static com.ab.util.Util.currMethodName;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Arrays;

public class SettingsPopup extends JDialog {
    static final boolean DEBUG_LOG = false;

    static final int MAX_NUMBER_SIZE = 4;

    private final PUtil pUtil = PUtil.getInstance();
    final String popupTitle = "Settings";
    final PConfig pConfig;
    final SettingsPopup popupInstance;
    final BufferedImage lineImage = pUtil.loadImage("buttons/radio.png");
    final BufferedImage selectedLineImage = pUtil.loadImage("buttons/radio-sel.png");
//    final ButtonPanel buttonPanel;

    final Host host;
    Rectangle popupRectangle;

    public SettingsPopup(Host host) {
        super(host.mainFrame(), true);
        this.host = host;
//        pConfig = PConfig.getInstance();
        pConfig = PConfig.getInstance();
        popupInstance = this;
        setTitle(m(popupTitle));

        setLayout(new BorderLayout(1, 4));
        popupRectangle = pConfig.settingsPopupRectangle.get();
        if (popupRectangle.width == 0) {
            popupRectangle = (Rectangle)pConfig.mainRectangle.get().clone();
        }
        this.setBounds(popupRectangle);
        this.setLocation(popupRectangle.x, popupRectangle.y);
        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Logger.printf(DEBUG_LOG, "SettingsPanel.%s -> %s\n", currMethodName(), e);
                popupRectangle = popupInstance.getBounds();
                pConfig.settingsPopupRectangle.set(popupRectangle);
            }

            @Override
            public void componentMoved(ComponentEvent e) {
                Logger.printf(DEBUG_LOG, "%s -> %s\n", currMethodName(), e);
                popupRectangle = popupInstance.getBounds();
                pConfig.settingsPopupRectangle.set(popupRectangle);
            }
        });

        // 1. list of settings
        JPanel settings = getSettingsPanel();
        JScrollPane scrollPane = new JScrollPane(settings);
        add(scrollPane, BorderLayout.CENTER);

        // 2. bottom buttons
        ButtonPanel buttonPanel = new ButtonPanel(1, 1,
            new PButton.ButtonHandler[][] {
                {new PButton.ButtonHandler(MainPanel.ButtonCommand.ok, buttonCommand -> save()),
                new PButton.ButtonHandler(MainPanel.ButtonCommand.cancel, buttonCommand -> cancel())
            }});
        add(buttonPanel, BorderLayout.SOUTH);

        setVisible(true);   // blocks until dialog ends
    }

    private void save() {
        pConfig.serialize();
//        PConfig.refresh();
        popupInstance.dispose();
    }

    private void cancel() {
        PConfig.refresh();  // restore configuration
        I18n.refresh(); // restore
        host.repaint();
        popupInstance.dispose();
    }

    JPanel getSettingsPanel() {
        JPanel settingsPanel = new JPanel();
        settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
        try {
            Class<? extends PConfig> claz = pConfig.getClass();
            for (Field property : claz.getFields()) {
                // including ColorProperty
                if (!property.getType().getName().endsWith("Property")) {
                    continue;
                }
                Object p = claz.getField(property.getName()).get(pConfig);
                if (((Config.Property<?>)p).getLabel().isEmpty()) {
                    continue;
                }
                settingsPanel.add(Box.createRigidArea(new Dimension(0,5)));
                settingsPanel.add(getPropUpdater(property));
            }
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return settingsPanel;
    }

    @SuppressWarnings("unchecked")
    JPanel getPropUpdater(final Field field) throws NoSuchFieldException, IllegalAccessException {
        Class<? extends PConfig> claz = pConfig.getClass();
        Object p = claz.getField(field.getName()).get(pConfig);
        final Config.Property<?> property = (Config.Property<?>)p;
        JPanel section = new JPanel();
        section.setLayout(new FlowLayout(FlowLayout.LEFT));
        Font font = new Font("Serif", Font.PLAIN, (int) (Metrics.getInstance().cardW * .3));
        int size = font.getSize();
        BufferedImage scaledLineImage = pUtil.scale(lineImage, size, size);
        Icon lineIcon = new ImageIcon(scaledLineImage);
        BufferedImage scaledSelectdLineImage = pUtil.scale(selectedLineImage, size, size);
        Icon selectedLineIcon = new ImageIcon(scaledSelectdLineImage);

        final String label = property.getLabel();
        final Object propValue = property.get();
        JComponent editor;
        if (propValue instanceof Tuple) {
            JPanel editorPanel = new JPanel();
            editorPanel.setLayout(new BoxLayout(editorPanel, BoxLayout.Y_AXIS));
            Tuple<?> tuple = (Tuple<?>) propValue;
            Logger.printf(DEBUG_LOG, "%s --> %s\n", label, tuple, Arrays.toString(tuple.getValues()));
            for (int i = 0; i < tuple.getValues().length; ++i) {
                final int index = i;
                final JTextField jTextField = new JTextField(tuple.getValues()[i].toString());
                jTextField.setFont(font);
                Dimension d = jTextField.getPreferredSize();
                d.width = I18n.maxPhraseLength * font.getSize() / 3;
                jTextField.setPreferredSize(d);
                jTextField.addFocusListener(new FocusAdapter() {
                    @Override
                    public void focusLost(FocusEvent e) {
                        Logger.printf(DEBUG_LOG, "%d: new value %s\n", index, jTextField.getText());
                        ((Tuple<String>)property.get()).getValues()[index] = jTextField.getText();
                        if (property.isVisual()) {
                            host.repaint();
                        }
                        super.focusLost(e);
                    }
                });
                editorPanel.add(jTextField);
            }
            editor = editorPanel;
        } else if (propValue instanceof Config.Selection) {
            Config.Selection<?> selection = (Config.Selection<?>) propValue;
            Logger.printf(DEBUG_LOG, "%s --> %d, %s\n", label, selection.getSelected(), Arrays.toString(selection.values));
            JList<?> jList = new JList<>(selection.values);
            jList.setCellRenderer((ListCellRenderer<Object>) (jList1, value, index, isSelected, cellHasFocus) -> {
                JLabel jLabel;
                String text = m(value.toString());
                if (isSelected) {
                    jLabel = new JLabel(text, selectedLineIcon, JLabel.LEFT);
                } else {
                    jLabel = new JLabel(text, lineIcon, JLabel.LEFT);
                }
                jLabel.setFont(font);
                Dimension d = jLabel.getPreferredSize();
                d.width = I18n.maxPhraseLength * font.getSize() / 3;
                jLabel.setPreferredSize(d);
                jLabel.setOpaque(true);
                return jLabel;
            });
            jList.setSelectedIndex(selection.getSelected());
            jList.addListSelectionListener(listSelectionEvent -> {
                ((Config.Selection<?>) propValue).setSelected(((JList<?>) listSelectionEvent.getSource()).getSelectedIndex());
                if (property.isVisual()) {
                    // in case of changing the language
                    I18n.refresh();
                    popupInstance.setTitle(m(popupTitle));
                    Container thisContainer = popupInstance.getContentPane();
                    thisContainer.validate();
                    thisContainer.repaint();

                    // repaint mail panel too
                    host.repaint();
                }
            });
            editor = jList;
        } else if (propValue instanceof Integer) {
            Logger.printf(DEBUG_LOG, "%s -> integer %s\n", label, propValue);
            final JTextField jTextField = new JTextField(MAX_NUMBER_SIZE);
            jTextField.setText(propValue.toString());
            jTextField.setHorizontalAlignment(SwingConstants.RIGHT);
            jTextField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    Logger.printf(DEBUG_LOG, "%s -> new value %s\n", label, jTextField.getText());
                    for (Field value : property.getClass().getDeclaredFields()) {
                        if (value.getName().equals("value")) {
                            value.setAccessible(true);  // just in case, all values must not be final
                            try {
                                value.set(property, Integer.parseInt(jTextField.getText()));
                            } catch (IllegalAccessException ex) {
                                throw new RuntimeException(ex);
                            }
                            break;
                        }
                    }
                    super.focusLost(e);
                }
            });
            jTextField.addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    if (!Character.isDigit(e.getKeyChar())) {
                        e.consume(); // ignore non-numeric input
                    }
                }
            });
            editor = jTextField;
        } else {
            editor = null;
            Logger.printf(DEBUG_LOG, "%s -> %s\n", label, propValue);
        }
        if (editor != null) {
            editor.setFont(font);
            section.add(editor);
        }

        TitledBorder sectionBorder = new TitledBorder(label) {
            @Override
            public void paintBorder(Component c,
                                    Graphics g,
                                    int x,
                                    int y,
                                    int width,
                                    int height) {
                TitledBorder border = (TitledBorder) ((JPanel)c).getBorder();
                border.setTitle(m(label));
                super.paintBorder(c, g, x, y, width, height);
            }
        };
        section.setBorder(sectionBorder);
        return section;
    }
}