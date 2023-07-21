package edu.umaryland.mri.gui;

import edu.umaryland.mri.data.Tools;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.util.Vector;

import javax.swing.JLabel;


public class GraphPanel extends JLabel {

	/**
	 * 
	 */
	private Vector<double[]> xData, yData;
	private Vector<Color> dataColor;
	private double xPad = .05;
	private double yPad = .05;
	private boolean autoscale = true;
	private double[] xLim = {0, 1}, yLim = {0, 1};
	private int scaleRange = 0;
	
	private static final long serialVersionUID = -3517250078689023060L;
	
	public GraphPanel() {
		xData = new Vector<double[]>(5);
		yData = new Vector<double[]>(5);
		dataColor = new Vector<Color>(5);
		this.setBackground(Color.WHITE);
		this.setDoubleBuffered(true);
		this.setOpaque(true);
	}
	
	public GraphPanel( double[] x, double[] y ) {
		this();
		if ( x.length == y.length ) {
			addDataPair(x, y);
		}
		scaleRange = x.length;
	}
	
	public GraphPanel( double[] y ) {
		this( Tools.linspace(0, 1, y.length), y );
	}
	
	public int getScaleRange() { return scaleRange; }
	public void setScaleRange( int sr ) { scaleRange = sr; }
	
	public double[] getXData( int index ) {
		return xData.get(index);
	}
	
	public double[] getYData( int index ) {
		return yData.get(index);
	}
	
	public void clearAllDataPairs() {
		xData.clear();
		yData.clear();
	}
	
	public boolean setDataPair( int index, double[] x, double[] y ) {
		if ( x.length != y.length || index >= xData.size() )
			return false;
		
		// make sure all of the data pairs are the same size
		int numPairs = xData.size();
		for ( int i=0; i<numPairs; ++i ) {
			double[] xx = (double[]) xData.get(i);
			if ( xx.length != x.length )
				return false;
		}
		
		xData.set(index, x);
		yData.set(index, y);
		
		if ( xData.size() == 1 ) // if this is the only data
			scaleRange = x.length;
		
		this.repaint();
		
		return true;
	}
	
	public boolean addDataPair( double[] x, double[] y ) {
		if ( x.length != y.length )
			return false;
		
		// make sure all of the data pairs are the same size
		int numPairs = xData.size();
		for ( int i=0; i<numPairs; ++i ) {
			double[] xx = xData.get(i);
			if ( xx.length != x.length )
				return false;
		}
		
		xData.add( x );
		yData.add( y );
		dataColor.add( Color.BLACK );
		
		if ( xData.size() == 1 ) // if this is the only data
			scaleRange = x.length;
		
		this.repaint();
		//this.getParent().repaint();
		
		return true;
	}
	
	public boolean addDataPair( double[] y ) {
		return addDataPair( Tools.linspace(0, 1, y.length), y );
	}
	
	public boolean setDataPair( int index, double[] y ) {
		return setDataPair( index, Tools.linspace(0, 1, y.length), y );
	}
	
	public int getNumDataPairs() {
		return xData.size();
	}
	
	public void setDataColor( int i, Color c ) {
		if ( i > xData.size() || i < 0 )
			return;
		
		dataColor.set(i,c);
	}
	
	public Color getDataColor( int i ) {
		if ( i > xData.size() || i < 0 )
			return null;
		
		return (Color) dataColor.get(i);
	}
	
	public void paintComponent(Graphics g) {
		  super.paintComponent(g);
		  Graphics2D g2d = (Graphics2D)g;
		  g2d.setRenderingHint (RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		  
		  // don't paint if there is no data
		  if ( xData.size() == 0 || yData.size() == 0 )
			  return;

		  Line2D.Double line2d = new Line2D.Double();
		  int width = this.getWidth(), height = this.getHeight(), dataLength;
		  double[] plotDataX, plotDataY, currDataX, currDataY;
		  
		  for ( int i=0; i<xData.size(); ++i ) {
			  // the range of the data needs to be rescaled to the window dimensions
			  currDataX = xData.get(i);
			  currDataY = yData.get(i);
			  dataLength = currDataX.length;
			  plotDataX = new double[dataLength];
			  plotDataY = new double[dataLength];
			  
			  if ( autoscale ) {
				  // scale the x data
				  double min = Double.MAX_VALUE;
				  double max = Double.MIN_VALUE;
				  for ( int j=0; j<dataLength; ++j ) {
					  if ( currDataX[j] > max )
						  max = currDataX[j];
					  if ( currDataX[j] < min )
						  min = currDataX[j];
				  }
				  for ( int j=0; j<dataLength; ++j ) {
					  plotDataX[j] = (currDataX[j]-min) / (max-min) * width;
				  }
				  
				  // scale the y data
				  min = Double.MAX_VALUE;
				  max = Double.MIN_VALUE;
				  for ( int j=0; j<scaleRange; ++j ) {
					  if ( currDataY[j] > max )
						  max = currDataY[j];
					  if ( currDataY[j] < min )
						  min = currDataY[j];
				  }
				  for ( int j=0; j<dataLength; ++j ) {
					  plotDataY[j] = (currDataY[j]-min) / (max-min) * height;
				  }
			  } else { // manual scaling of the data
				  // scale the x data
				  for ( int j=0; j<dataLength; ++j ) {
					  plotDataX[j] = (currDataX[j]-xLim[0]) / xLim[1] * width;
					  if ( plotDataX[j] < xLim[0] )
						  plotDataX[j] = xLim[0];
					  if ( plotDataX[j] > xLim[1] )
						  plotDataX[j] = xLim[1];
				  }
				  
				  // scale the y data
				  for ( int j=0; j<dataLength; ++j ) {
					  plotDataY[j] = (currDataY[j]-yLim[0]) / yLim[1] * width;
					  if ( plotDataY[j] < yLim[0] )
						  plotDataY[j] = yLim[0];
					  if ( plotDataY[j] > yLim[1] )
						  plotDataY[j] = yLim[1];
				  }			  
			  }
			  
			  g2d.setStroke( new BasicStroke(2) );
			  g2d.setColor( getDataColor(i) );
			  
			  for ( int j=0; j<dataLength-1; ++j ) {
				  line2d.setLine( plotDataX[j],  this.getHeight()-plotDataY[j], plotDataX[j+1],  this.getHeight()-plotDataY[j+1] );
				  g2d.draw( line2d );			  
			  }

		  }
	}

}
