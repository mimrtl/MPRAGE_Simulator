package edu.umaryland.mri.data;

import edu.umaryland.mri.data.Tools;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;

import ca.uol.aig.fftpack.ComplexDoubleFFT;

public class SimImage1D {
	
	private EPG_MPRAGE mprageA, mprageB;
	private ComplexDoubleFFT cdFFT, cdFFTB;
	private double[] phaseenc;
	private boolean[] maskA, maskB;
	
	public SimImage1D() {
		mprageA = new EPG_MPRAGE();
		mprageB = new EPG_MPRAGE();
	}
	
	public SimImage1D(SimImage1D that) {
		this.mprageA = new EPG_MPRAGE( that.mprageA );
		this.mprageB = new EPG_MPRAGE( that.mprageB );
		this.cdFFT = new ComplexDoubleFFT( that.mprageA.NP );
		this.phaseenc = that.phaseenc.clone();
		this.maskA = that.maskA.clone();
		this.maskB = that.maskB.clone();
	}

	public void setTissueAParams( double m0, double t1, double t2 ) {
		mprageA.setTissueParams(m0, t1, t2);
	}
	public void setTissueBParams( double m0, double t1, double t2 ) {
		mprageB.setTissueParams(m0, t1, t2);
	}
	
	public void setIdealSpoiling( boolean b ) {
		mprageA.setIdealSpoiling( b );
		mprageB.setIdealSpoiling( b );
	}	
	
	public void setSeqParams( double a, double i, double tr, double td1, double td2, int np ) {
		mprageA.setSeqParams( a, i, tr, td1, td2, np );
		mprageB.setSeqParams( a, i, tr, td1, td2, np );
		
		cdFFT = new ComplexDoubleFFT(np);
		cdFFTB = new ComplexDoubleFFT(np);
		
		phaseenc = Tools.linspace( -Math.PI, Math.PI, np );
		
		// define tissue masks
		int NP_2=(mprageA.NP)/2;
		int NP_4=(mprageA.NP)/4;
		int NP_8=(mprageA.NP)/8;

		maskA = new boolean[mprageA.NP];
		for ( int j=NP_4-NP_8; j<NP_2; j++ )
			maskA[j] = true;
		
		maskB = new boolean[mprageA.NP];
		for ( int j=NP_2; j<3*NP_4+NP_8; j++ )
			maskB[j] = true;		
	}
	
	public double[] calcTissueMean( double[] im1D ) {
		double meanA = 0, meanB = 0;
		int countA = 0, countB = 0, indR, indC;
		for ( int i=0; i<im1D.length/2; i++ ) {
			indR = 2*i;
			indC = indR+1;
			if ( maskA[i] ) {
				countA++;
				meanA += Math.hypot( im1D[indR], im1D[indC] );
			}
			if ( maskB[i] ) {
				countB++;
				meanB += Math.hypot( im1D[indR], im1D[indC] );
			}
		}
		meanA /= countA;
		meanB /= countB;
		return new double[] {meanA, meanB};
	}
	
	public double[] getKSpaceA() {
		return getKSpace( mprageA, maskA );		
	}	
	public double[] getKSpaceB() {
		return getKSpace( mprageB, maskB );		
	}
	
	public double[] doSim() {
		// perform simulation on tissue type a
		double[] kspace1 = getKSpace( mprageA, maskA );

		// perform simulation on tissue type b
		double[] kspace2 = getKSpace( mprageB, maskB );
		
		// combine the two tissues
		for ( int i=0; i<kspace1.length; i++ )
			kspace1[i] += kspace2[i];
		
		// fftshift
		Tools.fftshift1DComplex(kspace1);
		// take fft 
		cdFFT.bt(kspace1);
		// fftshift
		Tools.fftshift1DComplex(kspace1);
		
		return kspace1;
	}
	
	public double[] doSimThreaded(ExecutorService es) throws RejectedExecutionException {
		// setup simulation on tissue type a
		FutureTask<double[]> ftA = new FutureTask<double[]>( new KspaceCallA() );
		es.submit(ftA);
		
		// set simulation on tissue type b
		FutureTask<double[]> ftB = new FutureTask<double[]>( new KspaceCallB() );
		es.submit(ftB);
		
		// get results
		double[] kspace1, kspace2;
		try {
			kspace1 = ftA.get();
			kspace2 = ftB.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
		
		// combine the two tissues
		for ( int i=0; i<kspace1.length; i++ )
			kspace1[i] += kspace2[i];
		
		// fftshift
		Tools.fftshift1DComplex(kspace1);
		// take fft 
		cdFFT.bt(kspace1);
		// fftshift
		Tools.fftshift1DComplex(kspace1);
		
		return kspace1;
	}
	
	public class KspaceCallA implements Callable<double[]> {
		@Override
		public double[] call() throws Exception {
			return getKSpace(mprageA,cdFFT,maskA);
		}
	}
	public class KspaceCallB implements Callable<double[]> {
		@Override
		public double[] call() throws Exception {
			return getKSpace(mprageB,cdFFTB,maskB);
		}
	}
		
	private double[] getKSpace(EPG_MPRAGE mprage, boolean[] mask ) {
		return getKSpace( mprage, cdFFT, mask );
	}
	
	private double[] getKSpace(EPG_MPRAGE mprage, ComplexDoubleFFT fft, boolean[] mask) {
		// do simulation
		double[][] sim = mprage.doCalc();
		
		// copy into complex arrays
		double[] F = new double[mprage.NP*2];
		double[] phi   = new double[mprage.NP];
		for ( int i=0; i<mprage.NP; i++ ) {
			F[ 2*i   ] = sim[0][i];
			F[ 2*i+1 ] = sim[1][i];
			phi[ i ]   = sim[2][i];
		}
		
		double[] work1 = new double[mprage.NP*2]; // work array		
		double[] kspace = new double[mprage.NP*2]; // output array
		
		for ( int i=0; i<mprage.NP; i++ ) {			
			// copy into work array and mask
			for ( int j=0; j<mprage.NP; j++ ) {
				if ( mask[j] == true ) {
					work1[ 2*j ]   = F[ 2*i ];
					work1[ 2*j+1 ] = F[ 2*i+1 ];
				} else {
					work1[ 2*j ]   = 0;
					work1[ 2*j+1 ] = 0;
				}
			}
			
			// fftshift
			Tools.fftshift1DComplex(work1);
			// take fft
			fft.ft(work1);
			// fftshift
			Tools.fftshift1DComplex(work1);
			
			// do simulated phase encoding
			Complex c1 = new Complex( work1[2*i], work1[2*i+1] );
			Complex c2 = Complex.ComplexExp(phaseenc[i]);
			Complex c3 = Complex.ComplexExp(-phi[i]);
			c1.mul(c2);
			c1.mul(c3);
			
			kspace[2*i]   = c1.real;
			kspace[2*i+1] = c1.imag;
		}
		
		return kspace;
	}	
}
