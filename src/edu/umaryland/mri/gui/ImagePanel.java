package edu.umaryland.mri.gui;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;

import javax.swing.ImageIcon;
import javax.swing.JPanel;

public class ImagePanel extends JPanel {

    private Image img;

    public ImagePanel(String img) {
        this(new ImageIcon(img).getImage());
    }

    public ImagePanel(Image img) {
        setImage(img);
    }
    
    public void setImage(Image img) {
    	this.img = img;
        Dimension size = new Dimension(img.getWidth(null),img.getHeight(null));
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
        setSize(size);
        setLayout(null);
        this.repaint();
    }

    public void paintComponent(Graphics g) {
        g.drawImage(img,0,0,null);
    }

}