package edu.umaryland.mri.data;


/*
 * Created on May 18, 2005
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */

/**
 * @author Alan McMillan
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class Complex {
	
	protected double imag;
	protected double real;
	
	public Complex () {
		real = 0;
		imag = 0;
	}
	
	public Complex ( double re ) {
		real = re;
		imag = 0;
	}
	
	public Complex ( double re, double im ) {
		real = re;
		imag = im;
	}
	
	public void add ( Complex c ) {
		setReal(real + c.real);
		setImag(imag + c.imag);
	}
	
	public void sub ( Complex c ) {
		setReal(real - c.real);
		setImag(imag - c.imag);
	}
	
	public void mul ( Complex c ) {
		double tmpR = real;
		double tmpI = imag;
		setReal(tmpR*c.real - tmpI*c.imag);
		setImag(tmpI*c.real + tmpR*c.imag);
	}
	
	public void div ( Complex c ) {
		double tmpR = real;
		double tmpI = imag;
		setReal(tmpR*c.real + tmpI*c.imag);
		setReal(real / (c.real*c.real + c.imag*c.imag));
		setImag(tmpI*c.real - tmpR*c.imag);
		setImag(imag / (c.real*c.real + c.imag*c.imag));
	}
	
	public void div ( double f ) {
		setReal(real / f);
		setImag(imag / f);
	}
	
	public double angle () {
		return Math.atan2( imag, real );
	}	
	public double phase () {
		return Math.atan2( imag, real );
	}
	
	public double magn () {
		return Math.hypot( real, imag );
	}
	
	public void conj () {
		imag = -imag;
	}
	
	public static Complex ComplexExp( double angle ) {
		return new Complex( Math.cos( angle ), Math.sin( angle ) );
	}
	
	public String toString() {
		String s;
		if ( imag > 0 ) {
			s = real + " + " + imag + "i";
		} else if ( imag < 0 ) {
			s = real + " - " + -1*imag + "i";
		} else {
			s = real + "";
		}
		return s;
	}
	
	public static String getVectorString( Complex[] data ) {
		String dataVector = "";
		for ( int i=0; i<data.length; ++i ) {
			if ( i==0 ) {
				dataVector = dataVector + "[ " + data[i].toString();
			} else {
				dataVector = dataVector + ", " + data[i].toString();
			}
		}
		dataVector = dataVector + " ]";
		return dataVector;
	}

	public void setReal(double real) {
		this.real = real;
	}

	public double getReal() {
		return real;
	}

	public void setImag(double imag) {
		this.imag = imag;
	}

	public double getImag() {
		return imag;
	}
}
