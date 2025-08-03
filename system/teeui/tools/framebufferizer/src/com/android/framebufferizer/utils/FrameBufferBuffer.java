/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.framebufferizer.utils;

import com.android.framebufferizer.NativeRenderer;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.RenderedImage;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class FrameBufferBuffer extends JPanel implements ComponentListener, MouseMotionListener,
        MouseListener {
    public class MagnifiedView extends JPanel implements ComponentListener {
        private BufferedImage mImage;

        protected MagnifiedView() {
            addComponentListener(this);
        }

        @Override
        public void componentResized(ComponentEvent e) {
            synchronized (this) {
                mImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            }
        }

        @Override
        public void componentMoved(ComponentEvent e) {

        }

        @Override
        public void componentShown(ComponentEvent e) {

        }

        @Override
        public void componentHidden(ComponentEvent e) {

        }

        public int d() {
            return getWidth() / 5;
        }

        public void draw(RenderedImage image) {
            if (mImage == null)
                return;
            Graphics2D gc = mImage.createGraphics();
            gc.drawRenderedImage(image, AffineTransform.getScaleInstance(5.0, 5.0));
            repaint();
        }

        @Override
        public void paint(Graphics g) {
            synchronized (this) {
                g.drawImage(mImage, 0, 0, null);
            }
        }

    }

    public static enum EVENT_RESULT{
        NONE(0), CONFIRM(1) ,CANCEL(2);
        private int id;

        EVENT_RESULT(int id){
          this.id = id;
        }

        public int getValue(){
          return id;
        }
    }

    public class ConfigSelector extends JPanel implements ActionListener {
        private final String languages[];

        {
            languages = NativeRenderer.getLanguageIdList();
        }

        private final String layouts[] = NativeRenderer.getAvailableLayouts();
        private JComboBox<String> deviceSelector = new JComboBox(DeviceInfoDB.Device.values());
        private JCheckBox magnifiedCheckbox = new JCheckBox("Magnified");
        private JCheckBox invertedCheckbox = new JCheckBox("Inverted");

        private JComboBox<String> localeSelector = new JComboBox(languages);
        private JTextField confirmationMessage = new JTextField();

        private JComboBox<String> layoutSelector = new JComboBox(layouts);

        protected ConfigSelector() {
            System.err.println("ConfigSelector");
            this.setLayout(new GridBagLayout());

            GridBagConstraints c = null;

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0;
            this.add(new JLabel("Select Device:"), c);

            deviceSelector.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 0;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.gridwidth = 2;
            this.add(deviceSelector, c);

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 1;
            this.add(new JLabel("Select Locale:"), c);

            localeSelector.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 1;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            this.add(localeSelector, c);

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 2;
            this.add(new JLabel("Select Layout:"), c);

            layoutSelector.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 2;
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            this.add(layoutSelector, c);

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 3;
            this.add(new JLabel("UIOptions:"), c);

            magnifiedCheckbox.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 3;
            this.add(magnifiedCheckbox, c);

            invertedCheckbox.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 2;
            c.gridy = 3;
            this.add(invertedCheckbox, c);

            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 4;
            this.add(new JLabel("Confirmation message:"), c);

            confirmationMessage.setText(
                    "WWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWWW");
            confirmationMessage.addActionListener(this);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = 4;
            c.fill = GridBagConstraints.BOTH;
            c.gridwidth = 2;
            this.add(confirmationMessage, c);
        }

        public void setConfiguration() {
            Config config = Config.getInstance();
            config.setValue(Config.KEY_DEVICE, currentDevice());
            config.setValue(Config.KEY_LOCALE, getConfigSelector().currentLocale());
            config.setValue(Config.KEY_MAGNIFIED, getConfigSelector().magnified());
            config.setValue(Config.KEY_INVERTED, getConfigSelector().inverted());
            config.setValue(Config.KEY_MESSAGE, getConfigSelector().confirmationMessage());
            config.setValue(Config.KEY_LAYOUT, getConfigSelector().currentLayout());
        }

        public void actionPerformed(ActionEvent e) {
            setConfiguration();
            FrameBufferBuffer.this.renderNativeBuffer();
        }

        public DeviceInfoDB.Device currentDevice() {
            return (DeviceInfoDB.Device) deviceSelector.getSelectedItem();
        }

        public String currentLocale() {
            return (String) localeSelector.getSelectedItem();
        }

        public String currentLayout() {
            return (String) layoutSelector.getSelectedItem();
        }

        public boolean magnified() {
            return magnifiedCheckbox.isSelected();
        }

        public boolean inverted() {
            return invertedCheckbox.isSelected();
        }

        public String confirmationMessage() {
            return confirmationMessage.getText();
        }
    }

    private BufferedImage mImage;
    private DataBufferInt mBuffer;
    private MagnifiedView mMagnifiedView;
    private ConfigSelector mConfigSelector;
    private JFrame mFrame;
    private double mScale;

    public MagnifiedView getMagnifiedView() {
        if (mMagnifiedView == null) {
            mMagnifiedView = new MagnifiedView();
        }
        return mMagnifiedView;
    }

    public ConfigSelector getConfigSelector() {
        if (mConfigSelector == null) {
            mConfigSelector = new ConfigSelector();
        }
        return mConfigSelector;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.MOUSE_RELEASED == MouseEvent.MOUSE_RELEASED) {
            double x = e.getPoint().x / mScale;
            double y = e.getPoint().y / mScale;
            int value = NativeRenderer.onEvent((int)x, (int)y, MouseEvent.MOUSE_RELEASED);
            if(value == EVENT_RESULT.CONFIRM.getValue()){
                JOptionPane.showMessageDialog((Component) e.getSource(), "Confirm clicked.");
            } else if (value == EVENT_RESULT.CANCEL.getValue()){
                JOptionPane.showMessageDialog((Component) e.getSource(), "Cancel clicked.");
            }
        }
    }

    @Override
    public void mouseClicked(MouseEvent e){
    }

    @Override
    public void mousePressed(MouseEvent e){
    }

    @Override
    public void mouseEntered(MouseEvent e){
    }

    @Override
    public void mouseExited(MouseEvent e){
    }

    @Override
    public void mouseDragged(MouseEvent e) {

    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (mMagnifiedView != null) {
            final int rMask = 0xff;
            final int gMask = 0xff00;
            final int bMask = 0xff0000;
            final int bpp = 24;
            int d = mMagnifiedView.d();
            int x = e.getX() - d / 2;
            if (x + d > mImage.getWidth()) {
                x = mImage.getWidth() - d;
            }
            if (x < 0) {
                x = 0;
            }
            int y = e.getY() - d / 2;
            if (y + d > mImage.getHeight()) {
                y = mImage.getHeight() - d;
            }
            if (y < 0) {
                y = 0;
            }
            if (mImage.getWidth() < d) {
                d = mImage.getWidth();
            }
            if (mImage.getHeight() < d) {
                d = mImage.getHeight();
            }
            mMagnifiedView.draw(mImage.getSubimage(x, y, d, d));
        }
    }

    public void setDefaultValues() {
        Map<String, Object> configMap = Config.getInstance().getConfiguration();
        if (configMap != null) {
            Set set = configMap.entrySet();
            Iterator iterator = set.iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> element = (Map.Entry) iterator.next();
                switch (element.getKey()) {
                case Config.KEY_DEVICE:
                    getConfigSelector().deviceSelector
                            .setSelectedItem((DeviceInfoDB.Device.valueOf(((String) element.getValue()))));
                    break;
                case Config.KEY_LOCALE:
                    getConfigSelector().localeSelector.setSelectedItem((String) element.getValue());
                    break;
                case Config.KEY_MAGNIFIED:
                    getConfigSelector().magnifiedCheckbox.setSelected((Boolean) element.getValue());
                    break;
                case Config.KEY_INVERTED:
                    getConfigSelector().invertedCheckbox.setSelected((Boolean) element.getValue());
                    break;
                case Config.KEY_MESSAGE:
                    getConfigSelector().confirmationMessage.setText((String) element.getValue());
                    break;
                case Config.KEY_LAYOUT:
                    getConfigSelector().layoutSelector.setSelectedItem((String) element.getValue());
                    break;
                }
            }
        }
    }

    public FrameBufferBuffer() {
        setSize(412, 900);
        setPreferredSize(new Dimension(412, 900));
        setDefaultValues();
        renderNativeBuffer();
        addComponentListener(this);
        addMouseMotionListener(this);
        addMouseListener(this);
    }

    @Override
    public void componentResized(ComponentEvent e) {
        renderNativeBuffer();
        repaint();
    }

    @Override
    public void componentMoved(ComponentEvent e) {

    }

    @Override
    public void componentShown(ComponentEvent e) {

    }

    @Override
    public void componentHidden(ComponentEvent e) {

    }

    @Override
    public void paint(Graphics g) {
        synchronized (this) {
            g.drawImage(mImage, 0, 0, null);
        }
    }

    public void setFrame(JFrame frame) {
        mFrame = frame;
    }

    public void renderNativeBuffer() {
        final int LAYOUT_EXAMPLE_ERROR = -2;
        final int FRAME_BUFFER_ERROR = -1;
        DeviceInfo deviceInfo = DeviceInfoDB.getDeviceInfo(getConfigSelector().currentDevice());
        boolean magnified = getConfigSelector().magnified();
        boolean inverted = getConfigSelector().inverted();

        int w = deviceInfo.getWidthPx();
        int h = deviceInfo.getHeightPx();
        final int linestride = w;
        final int rMask = 0xff;
        final int gMask = 0xff00;
        final int bMask = 0xff0000;
        final int bpp = 24;
        int error = 0;
        synchronized (this) {
            mBuffer = new DataBufferInt(h * linestride);
            WritableRaster raster = Raster.createPackedRaster(mBuffer, w, h, linestride,
                    new int[] { rMask, gMask, bMask }, null);
            ColorModel colorModel = new DirectColorModel(bpp, rMask, gMask, bMask);
            BufferedImage image = new BufferedImage(colorModel, raster, true, null);
            NativeRenderer.setDeviceInfo(deviceInfo, magnified, inverted, getConfigSelector().currentLayout());
            NativeRenderer.setLanguage(getConfigSelector().currentLocale());
            NativeRenderer.setConfimationMessage(getConfigSelector().confirmationMessage());
            error = NativeRenderer.renderBuffer(0, 0, w, h, linestride, mBuffer.getData());
            if(error == FRAME_BUFFER_ERROR){
                System.out.println("Error framebuffer not initilized " + error);
            } else if(error == LAYOUT_EXAMPLE_ERROR){
                System.out.println("Error layout example not initilized " + error);
            } else if (error != 0) {
                System.out.println("Error rendering native buffer " + error);
            }

            mImage = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D gc = mImage.createGraphics();
            if (w / (double) h > getWidth() / (double) getHeight()) {
                mScale = (double) getWidth() / (double) w;
            } else {
                mScale = (double) getHeight() / (double) h;
            }
            gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            gc.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            gc.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            gc.drawRenderedImage(image, AffineTransform.getScaleInstance(mScale, mScale));
        }
        repaint();
    }
}
