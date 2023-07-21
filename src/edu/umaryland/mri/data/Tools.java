package edu.umaryland.mri.data;

import java.awt.Image;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.MemoryImageSource;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PixelGrabber;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.imageio.ImageIO;

public class Tools {
	public static double[] linspace( double low, double high, int n ) {
		double[] x = new double[n];
		for ( int i=0; i<(n-1); i++ )
			x[i] = low + i*(high-low)/(n-1);
		x[n-1] = high;
		return x;
	}
	
	public static void printArray( double[] a ) {
		for (int i=0; i<a.length; i++ ) {
				System.out.println( a[i] );
		}
	}
	
	public static void printComplexArray( double[] a ) {
		for (int i=0; i<a.length/2; i++ ) {
			if ( a[2*i+1] < 0 )
				System.out.println( a[2*i] + " - " + (-a[2*i+1]) + "i" );
			else
				System.out.println( a[2*i] + " + " + a[2*i+1] + "i" );
		}
	}
	
	public static double[] getComplexMagn( double[] a ) {
		double[] z = new double[a.length/2];
		for ( int i=0; i<a.length/2; i++ )
			z[i] = Math.hypot( a[2*i], a[2*i+1] );
		return z;
	}
	
	public static void fftshift1DComplex( double[] a ) {
		int n  = a.length/2; // number of real,imag pairs
		int n2 = (int) Math.ceil(n/2.0); // half length
		int index;
		double tmpR, tmpI;

		for (int i = 0; i < n2; i++) {
			index = 2*i;
			
			tmpR = a[index];
			tmpI = a[index+1];
			
			a[2*i]   = a[index+n];
			a[2*i+1] = a[index+n+1];
			
			a[index+n]   = tmpR;
			a[index+n+1] = tmpI;
		}
	}
	
	
	public static BufferedImage getGrayBufferedImageFromUShortArray( short[] data, int x, int y ) {
	    DataBuffer db = new DataBufferUShort( data, data.length );
	    
	    // Create a WritableRaster that will modify the image when pixels are modified
	    WritableRaster wr = Raster.createPackedRaster(db, x, y, Short.SIZE, null);

	    // Create a grayscale color model
	    ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
	    ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_USHORT);

	    // Finally, build the image from the
	    BufferedImage bi = new BufferedImage(cm,wr,false,null);
	    
	    return bi;
	}
	
	public static BufferedImage getFromImage( int[] d, int x, int y ) {
		BufferedImage bi = new BufferedImage(x,y,BufferedImage.TYPE_USHORT_GRAY);
		MultiPixelPackedSampleModel sm = new MultiPixelPackedSampleModel( DataBuffer.TYPE_INT, x, y, Integer.SIZE );
		
		DataBufferInt db = (DataBufferInt)sm.createDataBuffer();
		//sm.setPixels(0, 0, x, y, d, db );
				
		int[] data = db.getData();		
		System.arraycopy( d, 0, data, 0, d.length );

		WritableRaster wr = Raster.createWritableRaster( sm, db, null );
		bi.setData( wr );
		
		return bi;
	}
	
	public static BufferedImage getImageFromIntArray(int[] pixels, int width, int height) {
		//BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
        WritableRaster wr = image.getRaster();
        wr.setPixels(0,0,width,height,pixels);
        return image;
    }	
	
	public static byte[] getWritableByteArrayFromBufferedImage( BufferedImage bi ) {
		WritableRaster wras = (WritableRaster) bi.getData();
		//WritableRaster wras = bi.getRaster();
		DataBuffer db = wras.getDataBuffer();
		if ( db.getDataType() == DataBuffer.TYPE_BYTE )
			return ((DataBufferByte)db).getData();
		else
			return null;
	}
	
	public static BufferedImage getBufferedImageFromImageFile( String s ) {
		File f = new File(s);
		return getBufferedImageFromImageFile( f );
	}
	
	public static BufferedImage getBufferedImageFromImageFile( File f ) {
		BufferedImage bi;
		try {
			bi = ImageIO.read( f );
        } catch (IOException e) {
        	return null;
        }
        //ImageIO.read(getClass().getResource(imageName))
        return bi;
	}
	
	public static BufferedImage getBufferedImageFromImageFile( URL u ) {
		BufferedImage bi;
		try {
			bi = ImageIO.read( u );
        } catch (IOException e) {
        	return null;
        }
        return bi;
	}
	
	public static int[] getPixelsFromImageFile( File f ) {
		BufferedImage bi;
		try {
			bi = ImageIO.read( f );
        } catch (IOException e) {
        	return null;
        }
        
        return getPixelsFromBufferedImage( bi );        
	}
	
	public static int[] getPixelsFromBufferedImage( BufferedImage bi ) {
		int[] data = new int[bi.getWidth() * bi.getHeight()];
		PixelGrabber grabber;
	    grabber = new PixelGrabber(bi, 0, 0, bi.getWidth(), bi.getHeight(), data, 0, bi.getWidth());
	    try {
			grabber.grabPixels();
		} catch (InterruptedException e) {
			return null;
		}
		return data;
	}
}
