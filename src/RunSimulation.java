import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import edu.umaryland.mri.data.Tools;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Style;
import javax.swing.text.StyleContext;

import edu.umaryland.mri.data.EPG_MPRAGE;
import edu.umaryland.mri.data.SimImage1D;
import edu.umaryland.mri.data.SimImage1DRunner;
import edu.umaryland.mri.gui.GraphPanel;
import edu.umaryland.mri.gui.ImagePanel;


public class RunSimulation extends JFrame {

	private JTextArea textArea;
	private Document textDocument;
	private Style textStyle;
	private JProgressBar progressBar;
	private GraphPanel graphPanel;
	private ExecutorService es;
	private int[] gArray;
	private int[] wArray;
	private ImagePanel imagePanel;
	private static final long serialVersionUID = 6321935150798988371L;
	
    /*
     * Constructor, create GUI elements
     */
	public RunSimulation() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		} catch (UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
		
		// the simulations will call upon this executor service to create new threads
		es = Executors.newCachedThreadPool();
		
		this.getContentPane().setLayout( new BorderLayout() );		
		this.setDefaultCloseOperation(EXIT_ON_CLOSE);
		
		textArea = new JTextArea(15, 30);
		textArea.setEditable(false);
		textDocument = textArea.getDocument();
		textStyle = StyleContext.getDefaultStyleContext().getStyle( StyleContext.DEFAULT_STYLE );
		JScrollPane scrollPane = new JScrollPane(textArea);
		this.getContentPane().add(scrollPane,BorderLayout.CENTER);
		
		graphPanel = new GraphPanel();		
		//this.getContentPane().add(graphPanel,BorderLayout.CENTER);
		double[] flatLine = Tools.linspace(0, 0, 160);
		graphPanel.addDataPair(flatLine);
		
		getGrayWhiteArrays();
		imagePanel = new ImagePanel( getGrayWhiteImage(1) );
		this.getContentPane().add(imagePanel,BorderLayout.WEST);
		
		progressBar = new JProgressBar();
		progressBar.setMinimum(0);
		progressBar.setMaximum(100);
		progressBar.setStringPainted(true);
		this.getContentPane().add(progressBar,BorderLayout.PAGE_END);
		
		this.setIconImage( getGrayWhiteImage(2) );
		this.setTitle("MPRAGEsimulator");
		
		//this.setSize(500, (int) (scrollPane.getSize().height + imagePanel.getSize().height + progressBar.getSize().getHeight()) );
		this.setSize( 700, 400 );
		
		this.setLocationRelativeTo( null ); // position at center of the screen
		
		this.setVisible(true);
	}
	
	private void getGrayWhiteArrays() {
		//System.out.println(getClass().getResource("/gw.png"));
		//bi = Tools.getBufferedImageFromImageFile("gw.png");
		BufferedImage bi = Tools.getBufferedImageFromImageFile(getClass().getResource("gw.png"));
		
		gArray = new int[164*164];
		wArray = new int[164*164];
		
		int maskValue = 55000;
		
		byte[] data = Tools.getWritableByteArrayFromBufferedImage(bi);
		for ( int i=0; i<data.length/2; i++ ) {
			if ( data[2*i] == -1 )
				wArray[i] = maskValue;
			if ( data[2*i] == -122 )
				gArray[i] = maskValue;
		}
	}
	
	private BufferedImage getGrayWhiteImage( double contrastRatio ) {
		int[] bArray = new int[164*164];
		
		for ( int i=0; i<bArray.length; i++ ) {
			bArray[i] =  (int) (1.0*gArray[i]/contrastRatio + wArray[i]);
		}
		
		return Tools.getImageFromIntArray( bArray, 164, 164 );
	}
		
	public void shutdown() {
		es.shutdown();
		WindowEvent wev = new WindowEvent(this, WindowEvent.WINDOW_CLOSING);
        Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(wev);
	}
	
	/*
	 * Do simulated annealing
	 */
	public void doSimAnneal() {
		SimParamsComponent simParams = new SimParamsComponent();
		int result = JOptionPane.showConfirmDialog( this, simParams.getComponent(), "Get Simulation Parameters", JOptionPane.OK_CANCEL_OPTION);
		if ( result == JOptionPane.CANCEL_OPTION ) {			
			shutdown();
			return;
		}
		
		SimImage1D si = new SimImage1D();
		si.setTissueAParams( simParams.getPDa(), simParams.getT1a(), simParams.getT2a() );
		si.setTissueBParams( simParams.getPDb(), simParams.getT1b(), simParams.getT2b() );
		si.setIdealSpoiling(simParams.getIdealSpoil());
		
		double TR = simParams.getTR();
		double spoilInc = simParams.getSpoil();
		int NP = simParams.getNP();
		int faSt = simParams.getFAst();
		int faEn = simParams.getFAen();
		int faInc = simParams.getFAinc();
		int td1St  = simParams.getTD1st();
		int td1En  = simParams.getTD1en();
		int td1Inc = simParams.getTD1inc();
		int td2St  = simParams.getTD2st();
		int td2En  = simParams.getTD2en();
		int td2Inc = simParams.getTD2inc();
		
		double signalMin = simParams.getSMin();
		double contrastMin = simParams.getCMin();
			
		Random randGen = new Random();
		
		//String s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
		String s = "";
		s = s.concat( String.format("Tissue A: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1a(), simParams.getT2a(), simParams.getPDa()) );
		s = s.concat( String.format("Tissue B: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1b(), simParams.getT2b(), simParams.getPDb()) );
		s = s.concat( String.format("Seq. params: TR:%1$-4.4f spoil inc:%2$-4.4f NP:%3$-4d\n", TR, spoilInc, NP) );
		s = s.concat( String.format("FA start:%1$-4d FA incr.:%2$-4d FA end:%3$-4d\n", faSt, faInc, faEn) );
		s = s.concat( String.format("TD1 start:%1$-4d TD1 incr.:%2$-4d TD1 end:%3$-4d\n", td1St, td1Inc, td1En) );
		s = s.concat( String.format("TD2 start:%1$-4d TD2 incr.:%2$-4d TD2 end:%3$-4d\n", td2St, td2Inc, td2En) );
		s = s.concat( String.format("S_min:%1$-4.4f C_min:%2$-4.4f\n", signalMin, contrastMin) );
		printMessageLn(s);
		
		long st = System.currentTimeMillis();
		printMessageLn("Started simulated annealing...");
				
		double startTemp = 100;
		boolean isDone = false;
		int totalCount = 0;
		int currPosCount = 0;
		
		// keep track of overall best
		double bestSignal = 0;
		double bestContrast = 0;
		double bestFA = 0;
		double bestTD1 = 0;
		double bestTD2 = 0;
		double bestEnergy = 1e10;
		
		// initialize parameters
		double currTemp = startTemp;
		double currSignal = 0;
		double currContrast = 0;
		double currTime = 1e10;
		double currFA = faEn;//(faEn-faSt)/2;
		double currTD1 = td1En;//(td1En-td1St)/2;
		double currTD2 = td2En;//(td2En-td2St)/2;
		double currEnergy = 1e10;
		
		// we are more interested in signal and contrast than time, so use scalars to increase sensitivity
		double timeMin = NP*TR * td1St * td2St;
		//timeMin = 1500;
		double signalFactor = simParams.getSFactor();
		double contrastFactor = simParams.getCFactor();
		
		// calculate energy at starting point
		// set parameters
		si.setSeqParams(currFA, spoilInc, TR, currTD1, currTD2, NP);
		// do 1d simulation
		double[] im1d = si.doSim();						
		double[] meanSignal = si.calcTissueMean( im1d );
		double nextA = meanSignal[0]/NP; // note ifft is unnormalized, fix it here
		double nextB = meanSignal[1]/NP; // note ifft is unnormalized, fix it here
		
		double nextSignal = ( nextA > nextB ) ? nextB : nextA; // determine which signal is minimum
		double nextContrast = nextB/nextA;
		double nextTime = NP*TR + currTD1 + currTD2; // get time
		double nextEnergy = signalFactor*Math.pow( (nextSignal-signalMin)/signalMin, 2)  +  contrastFactor*Math.pow( (nextContrast-contrastMin)/contrastMin, 2)  +  Math.pow( (nextTime-timeMin)/timeMin, 2);
		
		// we start here, so this is currently the best energy
		currSignal = nextSignal;
		currContrast = nextContrast;
		currTime = nextTime;
		currEnergy = nextEnergy;
		
		// determine number of neighbors
		int width = 1;
		int numNeighbors = 0;
		for ( int fa=(int)  (currFA-width*faInc);    fa<=currFA+width*faInc;   fa=fa+faInc ) 
		for ( int td1=(int) (currTD1-width*td1Inc); td1<=currTD1+width*td1Inc; td1=td1+td1Inc )
		for ( int td2=(int) (currTD2-width*td2Inc); td2<=currTD2+width*td2Inc; td2=td2+td2Inc ) {
			numNeighbors++;
		}
		int currParams[][] = new int[numNeighbors+1][4]; // the current value and all neighbors
		
		while ( ! isDone ) {
			if  ( totalCount % 250 == 0 ) {				
				s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f E:%7$-4.4f", currFA, currTD1, currTD2, currSignal, currContrast, currTime, currEnergy);
				printMessageLn(s);
				//graphPanel.setDataPair( 0, Tools.getComplexMagn(im1d) );
				imagePanel.setImage( getGrayWhiteImage(currContrast) );
			}
			
			// set range of current parameters			
			int paramCount = 0; // current value is at index 0
			currParams[paramCount][0] = (int) currFA;
			currParams[paramCount][1] = (int) currTD1;
			currParams[paramCount][2] = (int) currTD2;
			currParams[paramCount][3] = 1;
			paramCount++;
			// now loop through parameters and set neighboring parameters
			for ( int fa=(int) (currFA-faInc);     fa<=currFA+faInc;   fa=fa+faInc ) 
			for ( int td1=(int) (currTD1-td1Inc); td1<=currTD1+td1Inc; td1=td1+td1Inc )
			for ( int td2=(int) (currTD2-td2Inc); td2<=currTD2+td2Inc; td2=td2+td2Inc ) {
					currParams[paramCount][0] = fa;
					currParams[paramCount][1] = td1;
					currParams[paramCount][2] = td2;
					// make sure parameter is valid in range
					if      ( (fa < faSt) | (fa > faEn) )
						currParams[paramCount][3] = -1;
					else if ( (td1 < td1St) | (td1 > td1En) )
						currParams[paramCount][3] = -1;
					else if ( (td2 < td2St) | (td2 > td2En) )
						currParams[paramCount][3] = -1;
					else
						currParams[paramCount][3] = 1;

					paramCount++;
			}
			
			// determine which neighbor we will try
			int currNeighbor = -1;
			while ( currNeighbor == -1 ) {
				currNeighbor = randGen.nextInt(numNeighbors-1) + 1;
				if ( currParams[currNeighbor][3] == -1 )
					currNeighbor = -1;
			}
			
			// set parameters
			si.setSeqParams(currParams[currNeighbor][0], spoilInc, TR, currParams[currNeighbor][2], currParams[currNeighbor][1], NP);
			// do 1d simulation
			//im1d = si.doSim();
			im1d = si.doSimThreaded(es);
			meanSignal = si.calcTissueMean( im1d );
			nextA = meanSignal[0]/NP; // note ifft is unnormalized, fix it here
			nextB = meanSignal[1]/NP; // note ifft is unnormalized, fix it here
			
			nextSignal = ( nextA > nextB ) ? nextB : nextA; // determine which signal is minimum
			nextContrast = nextB/nextA;
			nextTime = NP*TR + currParams[currNeighbor][1] + currParams[currNeighbor][2]; // get time
			nextEnergy = signalFactor*Math.pow( (nextSignal-signalMin)/signalMin, 2)  +  contrastFactor*Math.pow( (nextContrast-contrastMin)/contrastMin, 2)  +  Math.pow( (nextTime-timeMin)/timeMin, 2);
			
			double deltaE = currEnergy-nextEnergy;
			
			// compare energies
			if ( deltaE > 0 ) { // nextEnergy was less
				// move here
				currSignal = nextSignal;
				currContrast = nextContrast;
				currTime = nextTime;
				currFA = currParams[currNeighbor][0];
				currTD1 = currParams[currNeighbor][1];
				currTD2 = currParams[currNeighbor][2];
				currEnergy = nextEnergy;
				currPosCount = 0;
			} else {			
				// but maybe there is some chance we will move here anyway
				double p = Math.exp(deltaE/currTemp);
				double r = randGen.nextDouble();
				
				//System.out.println(deltaE);
			
				if ( r < p ) { // move here anyway
					// move here
					currSignal = nextSignal;
					currContrast = nextContrast;
					currTime = nextTime;
					currFA = currParams[currNeighbor][0];
					currTD1 = currParams[currNeighbor][1];
					currTD2 = currParams[currNeighbor][2];
					currEnergy = nextEnergy;
					currPosCount = 0;
				} else {
					currPosCount++;
					progressBar.setValue( (int) (100*currPosCount/50) );
				}
			}
			
			// keep track of absolute best
			if ( currEnergy < bestEnergy ) {
				bestSignal = currSignal;
				bestContrast = currContrast;
				bestFA = currFA;
				bestTD1 = currTD1;
				bestTD2 = currTD2;
				bestEnergy = currEnergy;
			}
			
			// stop when we haven't moved in 50 tries
			if ( currPosCount > 50 )
				isDone = true;			
			
			currTemp *= 0.995;
			totalCount++;
		}		
		long en = System.currentTimeMillis();
		progressBar.setValue(100);
		
		s = "Simulation ended here:\n";
		s += String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f E:%7$-4.4f", currFA, currTD1, currTD2, currSignal, currContrast, currTime, currEnergy);
		printMessageLn(s);
		
		//graphPanel.setDataPair( 0, Tools.getComplexMagn(im1d) );
		imagePanel.setImage( getGrayWhiteImage(bestContrast) );
		
		s = "Best found was:\n";
		s += String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f E:%7$-4.4f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, NP*TR+bestTD1+bestTD2, bestEnergy);
		printMessageLn(s);
		
		s = String.format("Elapsed time: %d s\nAvg. time per calc (for %d total): %4.4f ms", (en-st)/1000, totalCount, 1.0*(en-st)/totalCount );
		printMessageLn(s);				
		
	}
	
	
	/*
	 * Runs the simulation with multiple threads, one for each processor that can be detected
	 */
	public void doSimThreaded2() {		
		SimParamsComponent simParams = new SimParamsComponent();
		
		while (true) { // loop on this method
		
		// open simple window to get parameters		
		int result = JOptionPane.showConfirmDialog( this, simParams.getComponent(), "Get Simulation Parameters", JOptionPane.OK_CANCEL_OPTION);
		if ( result == JOptionPane.CANCEL_OPTION ) {			
			shutdown();
			return;
		}
		
		// determine number of threads we can run, create thread array, and "runner" array
		int cpuCores = Runtime.getRuntime().availableProcessors();
		
		Future<?>[] futureArray = new Future<?>[cpuCores];
		SimImage1DRunner[] simRunnerArray = new SimImage1DRunner[cpuCores];		
		for ( int i=0; i<cpuCores; i++ ) {
			simRunnerArray[i] = new SimImage1DRunner();
			simRunnerArray[i].setTissueAParams( simParams.getPDa(), simParams.getT1a(), simParams.getT2a() );
			simRunnerArray[i].setTissueBParams( simParams.getPDb(), simParams.getT1b(), simParams.getT2b() );
			simRunnerArray[i].setIdealSpoiling(simParams.getIdealSpoil());
		}		
		
		// get simulation parameters
		double TR = simParams.getTR();
		double spoilInc = simParams.getSpoil();
		int NP = simParams.getNP();
		int faSt = simParams.getFAst();
		int faEn = simParams.getFAen();
		int faInc = simParams.getFAinc();
		int td1St  = simParams.getTD1st();
		int td1En  = simParams.getTD1en();
		int td1Inc = simParams.getTD1inc();
		int td2St  = simParams.getTD2st();
		int td2En  = simParams.getTD2en();
		int td2Inc = simParams.getTD2inc();
		
		// optionally we can write to file
		boolean doFileWrite = simParams.getWriteFile();
		File writeFile = null;
		BufferedWriter bw = null;
		if ( doFileWrite ) {
			JFileChooser jc = new JFileChooser();
			int returnVal = jc.showSaveDialog(this);
			if ( returnVal == JFileChooser.CANCEL_OPTION )
				doFileWrite = false;
			else {
				writeFile = jc.getSelectedFile();
				try {
					bw = new BufferedWriter( new FileWriter( writeFile ) );
					bw.write("count,fa,ti,td,min_signal,contrast,total_time\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		// and display 1d images
		boolean liveView = simParams.getLiveView();
		
		// variables to keep track of the best protocol
		double signalMin = simParams.getSMin();		
		double contrastMin = simParams.getCMin();
		BestParamSearch paramSearch = new BestParamSearch(signalMin, contrastMin, signalMin, contrastMin, TR, NP);
		boolean bestFound = false;		
		
		//String s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
		String s = "";
		s = s.concat( String.format("Tissue A: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1a(), simParams.getT2a(), simParams.getPDa()) );
		s = s.concat( String.format("Tissue B: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1b(), simParams.getT2b(), simParams.getPDb()) );
		s = s.concat( String.format("Seq. params: TR:%1$-4.4f spoil inc:%2$-4.4f NP:%3$-4d\n", TR, spoilInc, NP) );
		s = s.concat( String.format("FA start:%1$-4d FA incr.:%2$-4d FA end:%3$-4d\n", faSt, faInc, faEn) );
		s = s.concat( String.format("TD1 start:%1$-4d TD1 incr.:%2$-4d TD1 end:%3$-4d\n", td1St, td1Inc, td1En) );
		s = s.concat( String.format("TD2 start:%1$-4d TD2 incr.:%2$-4d TD2 end:%3$-4d\n", td2St, td2Inc, td2En) );
		s = s.concat( String.format("S_min:%1$-4.4f C_min:%2$-4.4f\n", signalMin, contrastMin) );
		printMessageLn(s);
		
		long st = System.currentTimeMillis();
		printMessageLn("Started sim with " + cpuCores + " threads...");
		
		// create an array with all of the parameters we will try
		int total = (faEn-faSt+1) * ((td1En-td1St)/td1Inc+1) * ((td2En-td2St+1)/td2Inc+1);
		int params[][] = new int[total][3];
		int count = 0;
        for ( int fa=faSt; fa<=faEn; fa=fa+faInc )
		for ( int td1=td1St; td1<=td1En; td1=td1+td1Inc )
		for ( int td2=td2St; td2<=td2En; td2=td2+td2Inc ) {
			params[count][0] = fa;
			params[count][1] = td1;
			params[count][2] = td2;
			count++;
		}
		
        int[] currCounts = new int[cpuCores]; // used to keep track of the parameters used in each thread
		boolean isDone = false;		  
		count = 0;
		while ( !isDone ) {
			if  ( count % 250 == 0 )
				progressBar.setValue( 100 * count/total );
			
			// set parameters and run simulations on multiple threads 
			for ( int i=0; i<cpuCores; i++ ) {
				if ( count < total ) {
					simRunnerArray[i].setSeqParams(params[count][0], spoilInc, TR, params[count][1], params[count][2], NP);
					currCounts[i] = count;
					futureArray[i] = es.submit( simRunnerArray[i] );
					
					count++;
				} else {
					futureArray[i] = null;
					currCounts[i] = -1;
				}
			}

			// wait for threads to finish and get results
			for ( int i=0; i<cpuCores; i++ ) {
				if ( futureArray[i] != null ) {
					try {
						// we wait for each thread to finish
						futureArray[i].get();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (ExecutionException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					// no longer multi-threaded, run through each result one at a time
					double[] meanSignal = simRunnerArray[i].get1DTissueMean();
					double meanA = meanSignal[0]/NP; // note ifft is unnormalized, fix it here
					double meanB = meanSignal[1]/NP; // note ifft is unnormalized, fix it here
					double currMinSignal = (meanA < meanB ) ? meanA : meanB;
					double currContrast = meanB / meanA;
					double currFA = params[currCounts[i]][0];
					double currTD1 = params[currCounts[i]][1];
					double currTD2 = params[currCounts[i]][2];
					double currTime = currTD1+TR*NP+currTD2;
					
					if ( liveView )
						//graphPanel.setDataPair( 0, Tools.getComplexMagn(simRunnerArray[i].get1DSim()) );
						imagePanel.setImage( getGrayWhiteImage(currContrast) );
					
					if ( doFileWrite ) {
						s = String.format("%-6d,%-3d,%-4d,%-4d,%-6.6f,%6.6f,%-8.3f\n",count, currFA, currTD1, currTD2, currMinSignal, currContrast, currTime);
						try {
							bw.write(s);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					paramSearch.tryContrast(currMinSignal, currContrast, currFA, currTD1, currTD2);
					paramSearch.trySignal(currMinSignal, currContrast, currFA, currTD1, currTD2);
					if ( paramSearch.tryBest(currMinSignal, currContrast, currFA, currTD1, currTD2) ) {						
						// we found a better answer
						bestFound = true;
						
						printMessageLn("New optimal protocol found (#" + currCounts[i] + "):");
						s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", currFA, currTD1, currTD2, currMinSignal, currContrast, currTime);
						//s = String.format(" FA:%1$-2d TD1:%2$-4d TD2:%3$-4d S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", fa, td1, td2, bestSignal, bestContrast, bestTime);
						printMessageLn(s);
						
						//graphPanel.setDataPair( 0, Tools.getComplexMagn(simRunnerArray[i].get1DSim()) );
						imagePanel.setImage( getGrayWhiteImage(currContrast) );
					}
				} // threadArray
			} // cpuCores
			
			if ( count >= total )
				isDone = true;
		}
		
		if ( doFileWrite )
			try {
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		long en = System.currentTimeMillis();
		progressBar.setValue(100);
		s = String.format("Elapsed time: %d s\nAvg. time per calc (for %d total): %4.4f ms", (en-st)/1000, count, 1.0*(en-st)/count );
		printMessageLn(s);
		
		s = "Closest contrast match:\n" + String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", paramSearch.bestCParams.alpha, paramSearch.bestCParams.TD1, paramSearch.bestCParams.TD2, paramSearch.bestCParams.bestSignal, paramSearch.bestCParams.bestContrast, paramSearch.bestCParams.getTime());
		s += String.format( "\n TI:%1$-2.0f TR:%2$-4.0f ", paramSearch.bestCParams.TD1 + TR*NP/2, paramSearch.bestCParams.TD1 + TR*NP + paramSearch.bestCParams.TD2 );
		printMessageLn(s);
		s = "Closest signal match:\n" + String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", paramSearch.bestSParams.alpha, paramSearch.bestSParams.TD1, paramSearch.bestSParams.TD2, paramSearch.bestSParams.bestSignal, paramSearch.bestSParams.bestContrast, paramSearch.bestSParams.getTime());
		s += String.format( "\n TI:%1$-2.0f TR:%2$-4.0f ", paramSearch.bestSParams.TD1 + TR*NP/2, paramSearch.bestSParams.TD1 + TR*NP + paramSearch.bestSParams.TD2 );
		printMessageLn(s);	
		
		if ( !bestFound ) {
			printMessageLn("Sorry, a protocol matching your requirements could not be specified.");
		} else {
			s = "Most optimal protocol:\n" + String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", paramSearch.bestParams.alpha, paramSearch.bestParams.TD1, paramSearch.bestParams.TD2, paramSearch.bestParams.bestSignal, paramSearch.bestParams.bestContrast, paramSearch.bestParams.getTime());
			s += String.format( "\n TI:%1$-2.0f TR:%2$-4.0f ", paramSearch.bestParams.TD1 + TR*NP/2, paramSearch.bestParams.TD1 + TR*NP + paramSearch.bestParams.TD2 );
			printMessageLn(s);
			imagePanel.setImage( getGrayWhiteImage(paramSearch.bestParams.bestContrast) );
		}
		
		} // loop on this method 
	}
	
	
	/*
	 * Runs the simulation with multiple threads, one for each processor that can be detected
	 */
	public void doSimThreaded() {
		// open simple window to get parameters
		SimParamsComponent simParams = new SimParamsComponent();
		int result = JOptionPane.showConfirmDialog( this, simParams.getComponent(), "Get Simulation Parameters", JOptionPane.OK_CANCEL_OPTION);
		if ( result == JOptionPane.CANCEL_OPTION ) {			
			shutdown();
			return;
		}
		
		// determine number of threads we can run, create thread array, and "runner" array
		int cpuCores = Runtime.getRuntime().availableProcessors();
		Thread[] threadArray = new Thread[cpuCores];
		SimImage1DRunner[] simRunnerArray = new SimImage1DRunner[cpuCores];		
		for ( int i=0; i<cpuCores; i++ ) {
			simRunnerArray[i] = new SimImage1DRunner();
			simRunnerArray[i].setTissueAParams( simParams.getPDa(), simParams.getT1a(), simParams.getT2a() );
			simRunnerArray[i].setTissueBParams( simParams.getPDb(), simParams.getT1b(), simParams.getT2b() );
			simRunnerArray[i].setIdealSpoiling(simParams.getIdealSpoil());
		}
		
		// get simulation parameters
		double TR = simParams.getTR();
		double spoilInc = simParams.getSpoil();
		int NP = simParams.getNP();
		int faSt = simParams.getFAst();
		int faEn = simParams.getFAen();
		int faInc = simParams.getFAinc();
		int td1St  = simParams.getTD1st();
		int td1En  = simParams.getTD1en();
		int td1Inc = simParams.getTD1inc();
		int td2St  = simParams.getTD2st();
		int td2En  = simParams.getTD2en();
		int td2Inc = simParams.getTD2inc();
		
		// optionally we can write to file
		boolean doFileWrite = simParams.getWriteFile();
		File writeFile = null;
		BufferedWriter bw = null;
		if ( doFileWrite ) {
			JFileChooser jc = new JFileChooser();
			int returnVal = jc.showSaveDialog(this);
			if ( returnVal == JFileChooser.CANCEL_OPTION )
				doFileWrite = false;
			else {
				writeFile = jc.getSelectedFile();
				try {
					bw = new BufferedWriter( new FileWriter( writeFile ) );
					bw.write("count,fa,ti,td,min_signal,contrast,total_time\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		// and display 1d images
		boolean liveView = simParams.getLiveView();
		
		// variables to keep track of the best protocol
		double signalMin = simParams.getSMin();		
		double contrastMin = simParams.getCMin();		
		double bestSignal = 0;
		double bestContrast = 0;
		double bestTime = 1e10;
		double bestFA = 0, bestTD1 = 0, bestTD2 = 0;
		boolean bestFound = false;
		
		//String s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
		String s = "";
		s = s.concat( String.format("Tissue A: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1a(), simParams.getT2a(), simParams.getPDa()) );
		s = s.concat( String.format("Tissue B: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1b(), simParams.getT2b(), simParams.getPDb()) );
		s = s.concat( String.format("Seq. params: TR:%1$-4.4f spoil inc:%2$-4.4f NP:%3$-4d\n", TR, spoilInc, NP) );
		s = s.concat( String.format("FA start:%1$-4d FA incr.:%2$-4d FA end:%3$-4d\n", faSt, faInc, faEn) );
		s = s.concat( String.format("TD1 start:%1$-4d TD1 incr.:%2$-4d TD1 end:%3$-4d\n", td1St, td1Inc, td1En) );
		s = s.concat( String.format("TD2 start:%1$-4d TD2 incr.:%2$-4d TD2 end:%3$-4d\n", td2St, td2Inc, td2En) );
		s = s.concat( String.format("S_min:%1$-4.4f C_min:%2$-4.4f\n", signalMin, contrastMin) );
		printMessageLn(s);
		
		long st = System.currentTimeMillis();
		printMessageLn("Started sim with " + cpuCores + " threads...");
		
		// create an array with all of the parameters we will try
		int total = (faEn-faSt+1) * ((td1En-td1St)/td1Inc+1) * ((td2En-td2St+1)/td2Inc+1);
		int params[][] = new int[total][3];
		int count = 0;
        for ( int fa=faSt; fa<=faEn; fa=fa+faInc )
		for ( int td1=td1St; td1<=td1En; td1=td1+td1Inc )
		for ( int td2=td2St; td2<=td2En; td2=td2+td2Inc ) {
			params[count][0] = fa;
			params[count][1] = td1;
			params[count][2] = td2;
			count++;
		}
		
        int[] currCounts = new int[cpuCores]; // used to keep track of the parameters used in each thread
		boolean isDone = false;		  
		count = 0;
		while ( !isDone ) {
			if  ( count % 250 == 0 )
				progressBar.setValue( 100 * count/total );
			
			// set parameters and run simulations on multiple threads 
			for ( int i=0; i<cpuCores; i++ ) {
				if ( count < total ) {
					simRunnerArray[i].setSeqParams(params[count][0], spoilInc, TR, params[count][1], params[count][2], NP);
					currCounts[i] = count;
					threadArray[i] = new Thread(simRunnerArray[i]);
					threadArray[i].start();
					count++;
				} else {
					threadArray[i] = null;
					currCounts[i] = -1;
				}
			}
			
			// wait for threads to finish and get results
			for ( int i=0; i<cpuCores; i++ ) {
				if ( threadArray[i] != null ) {
					try {
						// we wait for each thread to finish
						threadArray[i].join();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					// no longer multi-threaded, run through each result one at a time
					double[] meanSignal = simRunnerArray[i].get1DTissueMean();
					double meanA = meanSignal[0]/NP; // note ifft is unnormalized, fix it here
					double meanB = meanSignal[1]/NP; // note ifft is unnormalized, fix it here
					double currMinSignal = (meanA < meanB ) ? meanA : meanB;
					
					if ( liveView )
						graphPanel.setDataPair( 0, Tools.getComplexMagn(simRunnerArray[i].get1DSim()) );
					
					if ( doFileWrite ) {
						s = String.format("%-6d,%-3d,%-4d,%-4d,%-6.6f,%6.6f,%-8.3f\n",count,params[currCounts[i]][0],params[currCounts[i]][1],params[currCounts[i]][2],currMinSignal,meanSignal[1]/meanSignal[0],TR*NP+params[currCounts[i]][1]+params[currCounts[i]][2]);
						try {
							bw.write(s);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					
					// determine which tissue has the minimum signal
					if ( currMinSignal >= signalMin ) {
						// now check contrast
						double currContrast = meanB / meanA;
						if ( currContrast >= contrastMin ) {
							// now check to see if it is a better answer
							if ( currContrast >= bestContrast ) {
								// check time
								double currTime = NP*TR + params[currCounts[i]][1] + params[currCounts[i]][2];
								if ( currTime <= bestTime ) {
									// finally, a better answer
									bestFound = true;
									bestFA = params[currCounts[i]][0];
									bestTD1 = params[currCounts[i]][1];
									bestTD2 = params[currCounts[i]][2];
									bestSignal = currMinSignal;
									bestContrast = currContrast;
									bestTime = currTime;
									
									printMessageLn("New optimal protocol found (#" + currCounts[i] + ")");
									s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
									//s = String.format(" FA:%1$-2d TD1:%2$-4d TD2:%3$-4d S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", fa, td1, td2, bestSignal, bestContrast, bestTime);
									printMessageLn(s);
									
									graphPanel.setDataPair( 0, Tools.getComplexMagn(simRunnerArray[i].get1DSim()) );
								} // currTime
							} // currContrast
						} // currContrast
					} // currMinSignal
				} // threadArray
			} // cpuCores
			
			if ( count >= total )
				isDone = true;
		}
		
		if ( doFileWrite )
			try {
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		long en = System.currentTimeMillis();
		progressBar.setValue(100);
		s = String.format("Elapsed time: %d s\nAvg. time per calc (for %d total): %4.4f ms", (en-st)/1000, count, 1.0*(en-st)/count );
		printMessageLn(s);
		
		if ( !bestFound ) {
			printMessageLn("Sorry, a protocol matching your requirements could not be specified.");
		}
	}

	public void doSim() {
		SimParamsComponent simParams = new SimParamsComponent();
		int result = JOptionPane.showConfirmDialog( this, simParams.getComponent(), "Get Simulation Parameters", JOptionPane.OK_CANCEL_OPTION );
		if ( result == JOptionPane.CANCEL_OPTION ) {			
			shutdown();
			return;
		}
		
		SimImage1D si = new SimImage1D();
		si.setTissueAParams( simParams.getPDa(), simParams.getT1a(), simParams.getT2a() );
		si.setTissueBParams( simParams.getPDb(), simParams.getT1b(), simParams.getT2b() );
		si.setIdealSpoiling(simParams.getIdealSpoil());
		double TR = simParams.getTR();
		double spoilInc = simParams.getSpoil();
		int NP = simParams.getNP();
		int faSt = simParams.getFAst();
		int faEn = simParams.getFAen();
		int faInc = simParams.getFAinc();
		int td1St  = simParams.getTD1st();
		int td1En  = simParams.getTD1en();
		int td1Inc = simParams.getTD1inc();
		int td2St  = simParams.getTD2st();
		int td2En  = simParams.getTD2en();
		int td2Inc = simParams.getTD2inc();
		
		double signalMin = simParams.getSMin();		
		double contrastMin = simParams.getCMin();
		
		boolean doFileWrite = simParams.getWriteFile();
		File writeFile = null;
		BufferedWriter bw = null;
		if ( doFileWrite ) {
			JFileChooser jc = new JFileChooser();
			int returnVal = jc.showSaveDialog(this);
			if ( returnVal == JFileChooser.CANCEL_OPTION )
				doFileWrite = false;
			else {
				writeFile = jc.getSelectedFile();
				try {
					bw = new BufferedWriter( new FileWriter( writeFile ) );
					bw.write("count,fa,ti,td,min_signal,contrast,total_time\n");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		boolean liveView = simParams.getLiveView();
		
		double bestSignal = 0;
		double bestContrast = 0;
		double bestTime = 1e10;
		double bestFA = 0, bestTD1 = 0, bestTD2 = 0;
		boolean bestFound = false;
		int total = (faEn-faSt+1) * ((td1En-td1St)/td1Inc+1) * ((td2En-td2St+1)/td2Inc+1);
		int count = 0;		
		long st = System.currentTimeMillis();
		
		
		//String s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
		String s = "";
		s = s.concat( String.format("Tissue A: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1a(), simParams.getT2a(), simParams.getPDa()) );
		s = s.concat( String.format("Tissue B: T1:%1$-4d T2:%2$-4d PD:%3$-4.4f\n", simParams.getT1b(), simParams.getT2b(), simParams.getPDb()) );
		s = s.concat( String.format("Seq. params: TR:%1$-4.4f spoil inc:%2$-4.4f NP:%3$-4d\n", TR, spoilInc, NP) );
		s = s.concat( String.format("FA start:%1$-4d FA incr.:%2$-4d FA end:%3$-4d\n", faSt, faInc, faEn) );
		s = s.concat( String.format("TD1 start:%1$-4d TD1 incr.:%2$-4d TD1 end:%3$-4d\n", td1St, td1Inc, td1En) );
		s = s.concat( String.format("TD2 start:%1$-4d TD2 incr.:%2$-4d TD2 end:%3$-4d\n", td2St, td2Inc, td2En) );
		s = s.concat( String.format("S_min:%1$-4.4f C_min:%2$-4.4f\n", signalMin, contrastMin) );
		printMessageLn(s);
		printMessageLn("Started...");
		
		for ( int fa=faSt; fa<=faEn; fa=fa+faInc )
		for ( int td1=td1St; td1<=td1En; td1=td1+td1Inc ) {
			progressBar.setValue(100*count/total);
		for ( int td2=td2St; td2<=td2En; td2=td2+td2Inc ) {
			count++;
			
			si.setSeqParams(fa, spoilInc, TR, td1, td2, NP);
			// do 1d simulation
			double[] im1d = si.doSim();						
			double[] meanSignal = si.calcTissueMean( im1d );
			double meanA = meanSignal[0]/NP; // note ifft is unnormalized, fix it here
			double meanB = meanSignal[1]/NP; // note ifft is unnormalized, fix it here
			
			if ( liveView )
				graphPanel.setDataPair( 0, Tools.getComplexMagn(im1d) );
			
			//s = String.format(" FA:%1$-2d TD1:%2$-4d TD2:%3$-4d", fa, td1, td2 );
			//printMessageLn(s);
			
			// determine which tissue has the minimum signal
			double currMinSignal = (meanA < meanB ) ? meanA : meanB; 
			
			if ( doFileWrite ) {
				s = String.format("%-6d,%-3d,%-4d,%-4d,%-6.6f,%6.6f,%-8.3f\n",count,fa,td1,td2,currMinSignal,meanSignal[1]/meanSignal[0],TR*NP+td1+td2);
				try {
					bw.write(s);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
			if ( currMinSignal >= signalMin ) {
				// now check contrast
				double currContrast = meanSignal[1] / meanSignal[0];
				if ( currContrast >= contrastMin )
					// now check to see if it is a better answer
					if ( currContrast >= bestContrast ) {
						// check time
						double currTime = TR*NP+td1+td2;
						if ( currTime <= bestTime ) {
							// finally, a better answer
							bestFound = true;
							bestFA = fa;
							bestTD1 = td1;
							bestTD2 = td2;
							bestSignal = currMinSignal;
							bestContrast = currContrast;
							bestTime = currTime;
							
							printMessageLn("New optimal protocol found (#" + count + ")");
							s = String.format(" FA:%1$-2.0f TD1:%2$-4.0f TD2:%3$-4.0f S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", bestFA, bestTD1, bestTD2, bestSignal, bestContrast, bestTime);
							//s = String.format(" FA:%1$-2d TD1:%2$-4d TD2:%3$-4d S:%4$-4.4f C:%5$-4.4f T:%6$-5.2f", fa, td1, td2, bestSignal, bestContrast, bestTime);
							printMessageLn(s);
							
							graphPanel.setDataPair( 0, Tools.getComplexMagn(im1d) );
						}
					}
			}
		}}		
		long en = System.currentTimeMillis();
		progressBar.setValue(100);
		s = String.format("Elapsed time: %d s\nAvg. time per calc (for %d total): %4.4f ms", (en-st)/1000, count, 1.0*(en-st)/count );
		printMessageLn(s);		
		
		if ( doFileWrite )
			try {
				bw.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
		if ( !bestFound ) {
			printMessageLn("Sorry, a protocol matching your requirements could not be specified.");
		}
	}
	
	private class SimParamsComponent {
		
		// components to set parameter values, initiated to default parameters
		private JPanel mainPanel = new JPanel();
		private JTextField PDa = new JTextField("1.0");
		private JTextField T1a = new JTextField("1450");
		private JTextField T2a = new JTextField("100");
		private JTextField PDb = new JTextField("0.92");
		private JTextField T1b = new JTextField("750");
		private JTextField T2b = new JTextField("75");
		private JTextField TR = new JTextField("7.12");
		private JTextField NP = new JTextField("160");
		private JTextField spoil = new JTextField("50");
		private JCheckBox idealSpoil = new JCheckBox();
		private JTextField FAst = new JTextField("5");
		private JTextField FAen = new JTextField("15");
		private JTextField FAinc = new JTextField("1");
		private JTextField TD1st = new JTextField("200");
		private JTextField TD1en = new JTextField("1000");
		private JTextField TD1inc = new JTextField("25");
		private JTextField TD2st = new JTextField("500");
		private JTextField TD2en = new JTextField("1500");
		private JTextField TD2inc = new JTextField("25");
		private JTextField sMin = new JTextField("0.02");
		private JTextField cMin = new JTextField("2");
		private JTextField sFactor = new JTextField("10");
		private JTextField cFactor = new JTextField("15");
		private JCheckBox writeFile = new JCheckBox();
		private JCheckBox liveView = new JCheckBox();
		
		// constructor, add components to panel
		public SimParamsComponent() {
			int numLines = 20;
			if ( currSearchType == SearchType.FULL ) {
				if ( currDistType == DistType.LOCAL )
					numLines = 23;
				else if ( currDistType == DistType.PUBLIC )
					numLines = 13;
			} else if ( currSearchType == SearchType.SIM_ANNEAL ) {
				if ( currDistType == DistType.LOCAL )
					numLines = 23;
				else if ( currDistType == DistType.PUBLIC )
					numLines = 23;
			}
			mainPanel.setLayout(new GridLayout(numLines,3));
			
			mainPanel.add(new JLabel("PDa: ")); mainPanel.add(PDa); mainPanel.add(new JLabel("[0-1]"));
			mainPanel.add(new JLabel("T1a: ")); mainPanel.add(T1a); mainPanel.add(new JLabel("ms"));
			mainPanel.add(new JLabel("T2a: ")); mainPanel.add(T2a); mainPanel.add(new JLabel("ms"));
			mainPanel.add(new JLabel("PDb: ")); mainPanel.add(PDb); mainPanel.add(new JLabel("[0-1]"));
			mainPanel.add(new JLabel("T1b: ")); mainPanel.add(T1b); mainPanel.add(new JLabel("ms"));
			mainPanel.add(new JLabel("T2b: ")); mainPanel.add(T2b); mainPanel.add(new JLabel("ms"));
			mainPanel.add(new JLabel("TR: ")); mainPanel.add(TR); mainPanel.add(new JLabel("ms"));
			mainPanel.add(new JLabel("NP: ")); mainPanel.add(NP); mainPanel.add(new JLabel(""));
			mainPanel.add(new JLabel("Spoil incr.: ")); mainPanel.add(spoil);  mainPanel.add(new JLabel("deg"));
			mainPanel.add(new JLabel("Ideal spoiling: ")); mainPanel.add(idealSpoil); mainPanel.add(new JLabel(""));
			if ( currDistType == DistType.LOCAL ) {
				mainPanel.add(new JLabel("FA start: ")); mainPanel.add(FAst); mainPanel.add(new JLabel("deg"));
				mainPanel.add(new JLabel("FA end: ")); mainPanel.add(FAen); mainPanel.add(new JLabel("deg"));
				mainPanel.add(new JLabel("FA incr.: ")); mainPanel.add(FAinc); mainPanel.add(new JLabel("deg"));
				mainPanel.add(new JLabel("TD1 start: ")); mainPanel.add(TD1st); mainPanel.add(new JLabel("ms"));
				mainPanel.add(new JLabel("TD1 end: ")); mainPanel.add(TD1en); mainPanel.add(new JLabel("ms"));
				mainPanel.add(new JLabel("TD1 incr.: ")); mainPanel.add(TD1inc); mainPanel.add(new JLabel("ms"));
				mainPanel.add(new JLabel("TD2 start: ")); mainPanel.add(TD2st); mainPanel.add(new JLabel("ms"));
				mainPanel.add(new JLabel("TD2 end: ")); mainPanel.add(TD2en); mainPanel.add(new JLabel("ms"));
				mainPanel.add(new JLabel("TD2 incr.: ")); mainPanel.add(TD2inc); mainPanel.add(new JLabel("ms"));
			}
			mainPanel.add(new JLabel("Smin: ")); mainPanel.add(sMin); mainPanel.add(new JLabel(""));
			mainPanel.add(new JLabel("Cmin: ")); mainPanel.add(cMin); mainPanel.add(new JLabel(""));
			if ( currDistType == DistType.LOCAL ) {
				mainPanel.add(new JLabel("Write file: ")); mainPanel.add(writeFile); mainPanel.add(new JLabel(""));
			}
			if ( currSearchType == SearchType.SIM_ANNEAL ) {
				mainPanel.add(new JLabel("Signal factor: ")); mainPanel.add(sFactor); mainPanel.add(new JLabel("x"));
				mainPanel.add(new JLabel("Contrast factor: ")); mainPanel.add(cFactor); mainPanel.add(new JLabel("x"));
			}
			if ( currSearchType == SearchType.FULL ) {
				mainPanel.add(new JLabel("Live view: ")); mainPanel.add(liveView); mainPanel.add(new JLabel(""));
			}
		}

		public JComponent getComponent() {
			return mainPanel;
		}
		
		/* methods to get parameter values */
		public double getPDa() { return Double.parseDouble(PDa.getText()); }
		public int getT1a() { return Integer.parseInt(T1a.getText()); }
		public int getT2a() { return Integer.parseInt(T2a.getText()); }
		public double getPDb() { return Double.parseDouble(PDb.getText()); }
		public int getT1b() { return Integer.parseInt(T1b.getText()); }
		public int getT2b() { return Integer.parseInt(T2b.getText()); }
		public double getTR() { return Double.parseDouble(TR.getText()); }
		public int getNP() { return Integer.parseInt(NP.getText()); }
		public double getSpoil() { return Double.parseDouble(spoil.getText()); }
		public boolean getIdealSpoil() { return idealSpoil.isSelected(); }
		public int getFAst() { return Integer.parseInt(FAst.getText()); }
		public int getFAen() { return Integer.parseInt(FAen.getText()); }
		public int getFAinc() { return Integer.parseInt(FAinc.getText()); }
		public int getTD1st() { return Integer.parseInt(TD1st.getText()); }
		public int getTD1en() { return Integer.parseInt(TD1en.getText()); }
		public int getTD1inc() { return Integer.parseInt(TD1inc.getText()); }
		public int getTD2st() { return Integer.parseInt(TD2st.getText()); }
		public int getTD2en() { return Integer.parseInt(TD2en.getText()); }
		public int getTD2inc() { return Integer.parseInt(TD2inc.getText()); }
		public double getSMin() { return Double.parseDouble(sMin.getText()); }
		public double getCMin() { return Double.parseDouble(cMin.getText()); }
		public double getSFactor() { return Double.parseDouble(sFactor.getText()); }
		public double getCFactor() { return Double.parseDouble(cFactor.getText()); }
		public boolean getWriteFile() { return writeFile.isSelected(); }
		public boolean getLiveView() { return liveView.isSelected(); }
	}
	
	
	/* handy methods to print text to textArea */
	public void printMessageLn( String s ) {
		printMessage( s+"\n");
	}
	public void printMessage( String s ) {
		try {
			textDocument.insertString( textDocument.getLength(), s, textStyle);
			textArea.setCaretPosition( textDocument.getLength() );
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
	}
	
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// create new RunSimulation object (the GUI)
		RunSimulation sim = new RunSimulation();
			
		// get parameters and run the simulation
		if ( currSearchType == SearchType.FULL )
			sim.doSimThreaded2();
		if ( currSearchType == SearchType.SIM_ANNEAL )
			sim.doSimAnneal();		
	}
	
	/*
	 * these change the way program runs
	 */
	private enum SearchType { FULL, SIM_ANNEAL }
	private enum DistType { LOCAL, PUBLIC }
	private static final DistType currDistType = DistType.LOCAL;
	private static final SearchType currSearchType = SearchType.FULL;	

	/*
	 * helper classes to store search parameters
	 */
	
	private class BestParamSearch {		
		private double desiredSignal, desiredContrast;
		private double minSignal, minContrast;
		public double TR;
		public int NP;
		private class OptParams {
			// used to keep to track of optimal values
			public double bestContrast = 0, bestSignal = 0, bestErr = Double.MAX_VALUE;
			// sequence parameters		
			public double alpha, TD1, TD2;
			public double getTime() { return TD1 + NP*TR + TD2; }
		}
		OptParams bestParams, bestCParams, bestSParams;
		
		public BestParamSearch( double ds, double dc, double ms, double mc, double tr, int np ) {
			desiredSignal = ds; 
			desiredContrast = dc;
			minSignal = ms;
			minContrast = mc;
			TR = tr;
			NP = np;
			bestParams = new OptParams();
			bestCParams = new OptParams();
			bestSParams = new OptParams();
		}
		
		public boolean tryBest(double s, double c, double a, double td1, double td2) {
			if ( s >= minSignal )
				if ( c >= minContrast ) {
					// check time
					double currErr = td1 + NP*TR + td2;
					if ( currErr <= bestParams.bestErr ) {
						bestParams.bestContrast = c;
						bestParams.bestSignal = s;
						bestParams.alpha = a;
						bestParams.TD1 = td1;
						bestParams.TD2 = td2;
						bestParams.bestErr = currErr;
						return true;
					}
				}
			return false;
		}
		
		public boolean tryContrast( double s, double c, double a, double td1, double td2 ) {
			double currErr = 0;
			// get SE
			currErr = c - desiredContrast;
			currErr *= currErr;
			// check if this one is better
			if ( currErr < bestCParams.bestErr ) {
				bestCParams.bestContrast = c;
				bestCParams.bestSignal = s;
				bestCParams.alpha = a;
				bestCParams.TD1 = td1;
				bestCParams.TD2 = td2;
				bestCParams.bestErr = currErr;
				return true;
			}
			return false;
		}
		
		public boolean trySignal( double s, double c, double a, double td1, double td2 ) {
			double currErr = 0;
			// get SE
			currErr = s - desiredSignal;
			currErr *= currErr;
			// check if this one is better
			if ( currErr < bestSParams.bestErr ) {
				bestSParams.bestContrast = c;
				bestSParams.bestSignal = s;
				bestSParams.alpha = a;
				bestSParams.TD1 = td1;
				bestSParams.TD2 = td2;
				bestSParams.bestErr = currErr;
				return true;
			}
			return false;
		}
	}
}

