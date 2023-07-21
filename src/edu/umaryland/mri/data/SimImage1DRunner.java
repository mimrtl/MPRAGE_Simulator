package edu.umaryland.mri.data;

import java.util.concurrent.Callable;

/*
 * This is a simple class that wraps SimImage1D into the Runnable interface so that 
 * the simulation can be run in a separate thread
 */
public class SimImage1DRunner implements Runnable {
		private SimImage1D si;
		private double[] im1d;
		private double[] imMean;
		private boolean bIsBusy;
		
		public SimImage1DRunner() {
			si = new SimImage1D();
			// use default parameters at 3T
			si.setTissueAParams(1.00, 1450, 100);
			si.setTissueBParams(0.92,  750,  75);
			si.setSeqParams(9, 50, 7.12, 330, 830, 160);
			bIsBusy = false;
		}
				
		public double[] get1DSim() { return im1d; }
		public double[] get1DTissueMean() { return imMean; }
		public boolean isBusy() { return bIsBusy; }
		public void setIdealSpoiling( boolean b ) { si.setIdealSpoiling(b); }		
		public void setTissueAParams( double pd, double t1, double t2 ) { si.setTissueAParams( pd, t1, t2 ); }		
		public void setTissueBParams( double pd, double t1, double t2 ) { si.setTissueBParams( pd, t1, t2 ); }
		public void setSeqParams(double fa, double spoilInc, double TR, double td1, double td2, int NP) {
			si.setSeqParams(fa, spoilInc, TR, td1, td2, NP);
		}

		@Override
		public void run() {
			bIsBusy = true;
			im1d = si.doSim();
			imMean = si.calcTissueMean( im1d );
			bIsBusy = false;
		}
}