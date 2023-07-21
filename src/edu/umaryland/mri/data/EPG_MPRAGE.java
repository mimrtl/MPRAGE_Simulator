package edu.umaryland.mri.data;

public class EPG_MPRAGE {
	
	// tissue parameters
	protected double M0, T1, T2;
	// sequence parameters
	protected double alpha, increment, TR, TD1, TD2;
	protected int NP;
	private boolean bIdealSpoiling;
	private boolean bVerbose;
	private double steadyStateEps;
	private int maxLoops;	
		
	/*
	 * Creates an MPRAGE object
	 */
	public EPG_MPRAGE() {
		maxLoops = 25;
		steadyStateEps=1e-10;
		bIdealSpoiling = false;
		bVerbose = false;
	}	
	
	public EPG_MPRAGE( EPG_MPRAGE that ) {
		// tissue parameters
		this.M0 = that.M0;
		this.T1 = that.T1;
		this.T2 = that.T2;
		// sequence parameters
		this.alpha = that.alpha;
		this.increment = that.increment;
		this.TR = that.TR;
		this.TD1 = that.TD1;
		this.TD2 = that.TD2;
		this.NP = that.NP;
		this.bIdealSpoiling = that.bIdealSpoiling;
		this.bVerbose = that.bVerbose;
		this.steadyStateEps = that.steadyStateEps;
		this.maxLoops = that.maxLoops;
	}

	/**
	 * Set relevant tissue parameters
	 * @param m0 Normalized equilibrium magnetization ([0,1])
	 * @param t1 T1 relaxation in milliseconds
	 * @param t2 T2 relaxation in milliseconds
	 */
	public void setTissueParams( double m0, double t1, double t2 ) {
		M0 = m0;
		T1 = t1;
		T2 = t2;
	}
	
	
	/**
	 * Set relevant pulse sequence parameters
	 * @param a Flip angle in degrees
	 * @param p Quadratic RF phase spoiling in degrees
	 * @param tr Repetition time in milliseconds
	 * @param td1 Delay time 1 (TI) in milliseconds
	 * @param td2 Delay time 2 (recovery) in milliseconds
	 * @param np Length of RF pulse train
	 */
	public void setSeqParams( double a, double i, double tr, double td1, double td2, int np ) {
		alpha = Math.toRadians(a);
		increment = Math.toRadians(i);
		TR = tr;
		TD1 = td1;
		TD2 = td2;
		NP = np;
	}
	
	
	/**
	 * Does MPRAGE calculation. Returns 2D array of size (NPx3) where dimension 1 is the real component
	 * of the transverse magnetization, dimension 2 is the imaginary component of the transverse
	 * magnetization, and dimension 3 is the phase of the RF pulse used for RF spoiling 
	 * 
	 * @return
	 */
	public double[][] doCalc() {
		
		if ( bVerbose ) {
			if ( !bIdealSpoiling )
				System.out.println("Running EPG simulation...");
			else
				System.out.println("Running ideal spoiling simulation...");
			System.out.println("M0=" + M0 + "\tT1=" + T1 + " ms\tT2=" + T2 + " ms");
			System.out.println("alpha=" + Math.toDegrees(alpha) + " degrees\tRF spoiling increment=" + Math.toDegrees(increment) + " degrees");
			System.out.println("# of inner TRs=" + NP + "\tinner TR=" + TR + " ms\tTD1=" + TD1 + " ms\tTD2=" + TD2 + " ms");
		}
		
		// output array (transverse magnetization)
	    double[][] output = new double[3][NP];
	    
	    // variables used to control iterations towards steady state Mz
		boolean bIsSteadyState = false;
		int numLoops = 0;

		// initialize constants and arrays used in calculation
	    double er1=Math.exp(-TR/T1);
	    double er2=Math.exp(-TR/T2);
	    double ed1_1=Math.exp(-TD1/T1);
	    double ed1_2=Math.exp(-TD1/T2);
	    double ed2_1=Math.exp(-TD2/T1);
	    double ed2_2=Math.exp(-TD2/T2);
	    double[] fx = new double[2*NP+1];
	    double[] fy = new double[2*NP+1];
	    double[] pfx = new double[2*NP+1];
	    double[] pfy = new double[2*NP+1];
	    double[] pzx=new double[NP+1];
	    double[] pzy=new double[NP+1];	    
	    double[] zx = new double[NP+1];
	    double[] zy = new double[NP+1];
	    double[] zx_ = new double[NP+1];
	    double[] zy_ = new double[NP+1];
	    for ( int k=0; k < zx.length; k++ ) {
	    	zx_[k] = Double.POSITIVE_INFINITY;
	    	zy_[k] = Double.POSITIVE_INFINITY;
	    }
	    zx[1]=M0; // initial magnetization
	    
	    double beta=Math.PI; // flip angle of inversion pulse
		
		// RF spoiling variables
	    double INCREMENT=0; // quadratically increasing RF phase offset
		double phi=0; // current RF phase
		double[] PHI = new double[NP+1]; // used to keep track of RF phase
	    
	    // ideally we loop until we get to steady state M_z
		while ( !bIsSteadyState ) {
			numLoops++;
				    	
	        phi=0;
	        INCREMENT=0;

	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	        //% TD1
	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	        double e1=ed1_1;
	        double e2=ed1_2;
	        
	        double a=Math.cos(beta/2)*Math.cos(beta/2);
	        double b=Math.sin(beta/2)*Math.sin(beta/2);
	        double c=Math.sin(beta);
	        double d=Math.cos(beta);
	        double e=Math.sin(0);
	        double f=Math.cos(0);
	        double g=Math.sin(2*0);
	        double h=Math.cos(2*0);
	        double hb=h*b;
	        double gb=g*b;
	        double ec=e*c;
	        double fc=f*c;
	                	
	        for ( int k=0; k<=NP-1; k++ ) {
            
	        	int n=NP+k;
	        	int m=NP-k;
	        	
	        	pfx[n]=a*fx[n]+hb*fx[m]+gb*fy[m]+ec*zx[k+1]+fc*zy[k+1];
	        	pfy[n]=a*fy[n]-hb*fy[m]+gb*fx[m]-fc*zx[k+1]+ec*zy[k+1];
	        	pfx[m]=hb*fx[n]+gb*fy[n]+a*fx[m]+ec*zx[k+1]-fc*zy[k+1];
	        	pfy[m]=gb*fx[n]-hb*fy[n]+a*fy[m]-fc*zx[k+1]-ec*zy[k+1];
	        	pzx[k+1]=(-ec*fx[n]+fc*fy[n]-ec*fx[m]+fc*fy[m]+2*d*zx[k+1])/2;
	        	pzy[k+1]=(-fc*fx[n]-ec*fy[n]+fc*fx[m]+ec*fy[m]+2*d*zy[k+1])/2;
	        	
	        }
	        	        
	        for ( int k=-(NP-1); k <= (NP-1); k++ ) {
	        	int n=NP+k;
	            fx[n+1]=pfx[n]*e2;
	            fy[n+1]=pfy[n]*e2;

	            if ( k > 0 )		            	
	                zx[k+1]=pzx[k+1]*e1;


	            if ( k == 0 )		                
	                zx[k+1]=pzx[k+1]*e1+M0*(1-e1);

	            if ( k >= 0 )
	                zy[k+1]=pzy[k+1]*e1;
	        }
	        	        		        
	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	        //% alpha pulse train
	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	        int t=1;
	    	
	        e1=er1;
	        e2=er2;
	        
	        for ( int j=0; j<=NP-1; j++ ) {
	            
	            INCREMENT = (INCREMENT+increment) % (2*Math.PI);
	            phi= (phi+INCREMENT) % (2*Math.PI);
	            PHI[t-1]=phi;

	            a=Math.cos(alpha/2)*Math.cos(alpha/2);
	            b=Math.sin(alpha/2)*Math.sin(alpha/2);
	            c=Math.sin(alpha);
	            d=Math.cos(alpha);
	            e=Math.sin(phi);
	            f=Math.cos(phi);
	            g=Math.sin(2*phi);
	            h=Math.cos(2*phi);
	            hb=h*b;
	            gb=g*b;
	            ec=e*c;
	            fc=f*c;
	            
	            for ( int k=0; k<=j; k++ ) {
	                
	                int n=NP+k;
	                int m=NP-k;
	                
	                pfx[n]=a*fx[n]+hb*fx[m]+gb*fy[m]+ec*zx[k+1]+fc*zy[k+1];
	                pfy[n]=a*fy[n]-hb*fy[m]+gb*fx[m]-fc*zx[k+1]+ec*zy[k+1];
	                pfx[m]=hb*fx[n]+gb*fy[n]+a*fx[m]+ec*zx[k+1]-fc*zy[k+1];
	                pfy[m]=gb*fx[n]-hb*fy[n]+a*fy[m]-fc*zx[k+1]-ec*zy[k+1];
	                pzx[k+1]=(-ec*fx[n]+fc*fy[n]-ec*fx[m]+fc*fy[m]+2*d*zx[k+1])/2;
	                pzy[k+1]=(-fc*fx[n]-ec*fy[n]+fc*fx[m]+ec*fy[m]+2*d*zy[k+1])/2;
	                
	            }

	            output[0][t-1] = pfx[NP];
	            output[1][t-1] = pfy[NP];
	            output[2][t-1] = PHI[t-1];
	            
	            for ( int k=-j; k<=j; k++ ) {
                
	                int n=NP+k;
	                fx[n+1]=pfx[n]*e2;
	                fy[n+1]=pfy[n]*e2;
	                
	                if ( k > 0 )
	                    zx[k+1]=pzx[k+1]*e1;

	                if ( k == 0 )
	                    zx[k+1]=pzx[k+1]*e1+M0*(1-e1);

	                if ( k >= 0 )
	                    zy[k+1]=pzy[k+1]*e1;
	            }
	            
	            // clear transverse magnetization if we are using ideal spoiling
				if ( bIdealSpoiling ) {
					for ( int k=0; k<fx.length; k++ ) {
						fx[k] = 0;
						fy[k] = 0;
					}
				}

	            t++;
	            
	        }

	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	        //% TD2
	        //%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	        e1=ed2_1;
	        e2=ed2_2;

	        // assume complete loss of transverse magnetization during longitudinal recovery phase
        	for ( int k=0; k<2*NP; k++ ) {
        		fx[k] = 0;
        		fy[k] = 0;
        	}

        	for ( int k=-(NP-1); k<=(NP-1); k++ ) {
	            
	            //int n = NP+k;

	            if ( k > 0 )
	                zx[k+1]=pzx[k+1]*e1*er1;

	            if ( k == 0 )
	                zx[k+1]=pzx[k+1]*e1*er1+M0*(1-e1*er1);


	            if ( k >= 0 )
	                zy[k+1]=pzy[k+1]*e1*er1;

        	}

        	//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
			//% check to see if we have reached steady state
			//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
			double zErr = 0, magnz = 0, magnz_ = 0;
			for ( int k=0; k<zx.length; k++ ) {
				magnz  = Math.sqrt( zx[k]*zx[k] + zy[k]*zy[k] );
        		magnz_ = Math.sqrt( zx_[k]*zx_[k] + zy_[k]*zy_[k] );
        		zErr += Math.abs( magnz - magnz_ );
			}
			if ( zErr < steadyStateEps ) {
				bIsSteadyState = true;
				if ( bVerbose )
					System.out.println("Finshed. Steady state was reached after " + numLoops + " iterations [err=" + zErr + ", tol=" + steadyStateEps + "]");
			}
			if ( numLoops >= maxLoops ) {
				bIsSteadyState = true;
				if ( bVerbose )
					System.out.println("Finshed. Steady state was not reached after " + numLoops + " iterations [err=" + zErr + ", tol=" + steadyStateEps + "]");	
			}
			
			//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
			//% store current Mz as previous
			//%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%			
			for ( int k=0; k<zx.length; k++ ) {
	        	zx_[k]= zx[k];
		        zy_[k]= zy[k];				
			}
	    }		
		
		//for ( int k=0; k<output.length; k++ ) {
		//	System.out.println(output[k][0] + ",\t" + output[k][1] + "i");
		//}
		    
		return output;
	}
	
	/**
	 * Sets ideal spoiling (transverse magnetization is assumed to be zero at the end of each RF pulse) [default is false]
	 * @param bIdealSpoiling
	 */
	public void setIdealSpoiling(boolean bIdealSpoiling) { this.bIdealSpoiling = bIdealSpoiling; }
	public boolean isIdealSpoiling() { return bIdealSpoiling; }

	/**
	 * Sets verbose mode (program prints additional information to stdout) [default is false]
	 * @param bVerbose
	 */
	public void setVerbose(boolean bVerbose) { this.bVerbose = bVerbose; }
	public boolean isVerbose() { return bVerbose; }
	
	/**
	 * Sets stopping criteria for each steady state [default is 1e-10]
	 * @param steadyStateEps
	 */
	public void setSteadyStateEps(double steadyStateEps) { this.steadyStateEps = steadyStateEps; }
	public double getSteadyStateEps() { return steadyStateEps; }

	/**
	 * Sets maximum number of loops allowed to reach steady state [default is 25]
	 * @param maxLoops
	 */
	public void setMaxLoops(int maxLoops) { this.maxLoops = maxLoops; }
	public int getMaxLoops() { return maxLoops; }

}
