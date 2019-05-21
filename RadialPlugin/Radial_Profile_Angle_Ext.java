import ij.*;
import ij.gui.*;
import ij.util.*;
import ij.plugin.filter.*;
import ij.plugin.filter.PlugInFilter;
import ij.plugin.frame.RoiManager;
import ij.process.*;
import ij.measure.*;
import ij.measure.Calibration;
import ij.text.*;
import ij.macro.Functions;
import ij.macro.MacroExtension;
import ij.macro.ExtensionDescriptor;
import java.awt.*;
import java.awt.event.*;
import java.awt.TextField;
import java.util.*;
import multi_plot.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


/** This plugin is an update of the Radial Profile plugin from Paul Baggethun.
 *  http://rsb.info.nih.gov/ij/plugins/radial-profile.html
 *  This plugin lets you choose the starting angle and integration angle
 *  over which the integration on the defined circle is done.
 *  The integration will be done over an area defined by :
 *  starting angle +/- integration angle.
 *  The size and the position of the Roi can be defined and modified by
 *  either using the plugin menu, the mouse or shortkeys on keyboard.
 *  Additionnally, the integration calculation can be done over a whole stack.
 *
 *  The plugin is implementing MultyPlotWindow developped in the Color_Profiler
 *  routine by Dimiter Prodanov (University of Leiden).
 *
 *  The plugin includes also a routine abling the calculation of the radius
 *  of the generated Roi and saving the data in a result panel.
 *
 *  The new version implements macro recording and excecution features.
 *  And following several discussions and advices with Michael Schmid
 *  (schmid@iap.tuwien.ac.at) the ROI position and size can be modified
 *  with the mouse and the opening angle cone is always drawn together
 *  with the round ROI.
 *  
 *  Finally the plugin takes also into account a bug correction introduced
 *  by Kota Miura (miura@embl.de).
 *  
 *  First version: 4-20-2005
 *  Last updated: 1-14-2014 (requires: ImageJ 1.38p or higher)
 *  author : Philippe Carl (philippe.carl@unistra.fr)
*/

public class Radial_Profile_Angle_Ext implements PlugInFilter, ImageListener, ActionListener, KeyListener, DialogListener, MouseMotionListener, MouseWheelListener, Measurements, MacroExtension
{
	private static Radial_Profile_Angle_Ext instance;
	private final static int X_CENTER = 0, Y_CENTER = 1, RADIUS = 2, START_ANGLE = 3, INT_ANGLE = 4;
	ImagePlus imp;
	ImageProcessor ip2;
	ImageCanvas canvas;
	Rectangle rct;
	MultyPlotExt plot;
	ShapeRoi s1, s2;
	Overlay overlay;
	NonBlockingGenericDialog gd;
	boolean previousRequireControlKeyState;
	String[] items = {"Plot Droplet with integration angle", "Plot integration angle"};
	static boolean useCalibration    = true;
	static boolean makeStackAnalysis = false;
	static int shift_button_mask  = InputEvent.SHIFT_DOWN_MASK | InputEvent.BUTTON1_DOWN_MASK;
	static int ctrl_mask          = InputEvent.CTRL_DOWN_MASK;
	static int  alt_mask          = InputEvent. ALT_DOWN_MASK;
	static int  alt_shift_mask    = InputEvent. ALT_DOWN_MASK + InputEvent.SHIFT_DOWN_MASK;
	static int  alt_ctrl_mask     = InputEvent. ALT_DOWN_MASK + InputEvent.CTRL_DOWN_MASK;
	int nBins = 100;
	int[]   xPoint = new int[6];
	int[]   yPoint = new int[6];
	int slice;
	int Sa;			// Starting angle in degree over which the calculation is done
	int Ia;			// Integration angle in degree over which the calculation is done
	double X0;		// X center in pixels of the circle over which the calculation is done
	double Y0;		// Y center in pixels of the circle over which the calculation is done
	double mR;		// Radius in pixels of the circle over which the calculation is done
	double cosMin, cosMax, sinMin, sinMax;
	float []   dataX;	// X data of the plot
	float [][] dataY;	// Y data of the plot
	TextField[] numericFields;
	Button button0, button1;
	CheckboxGroup cbg;
	Checkbox cb0, cb1;

	public int setup(String arg, ImagePlus imp)
	{
		if (arg.equals("about"))
		{
			showAbout();
			return DONE;
		}
		if (IJ.versionLessThan("1.48p"))
			return DONE;

		this.imp = imp;

		if (instance != null && instance.getDialog() != null)
		{
			instance.getDialog().toFront();
			ImageWindow win = instance.getImagePlus().getWindow();
			if (win != null) win.toFront();
			return DONE;
		} else
			instance = this;

		if (instance.getImagePlus() != null && instance.getImagePlus().getWindow() != null)
			instance.getImagePlus().getWindow().toFront();

		return DOES_ALL + NO_UNDO + NO_CHANGES;
	}

	public void run(ImageProcessor ip)
	{
		imp.unlock();
		ip2 = ip;
		ImageWindow win = imp.getWindow();
		win.addKeyListener(this);
		ImagePlus.addImageListener(this); 
		canvas = win.getCanvas();
		canvas.addKeyListener(this);
		canvas.addMouseMotionListener(this);
		canvas.addMouseWheelListener(this);
		previousRequireControlKeyState = Prefs.requireControlKey;
		Prefs.requireControlKey = true;
		setXYcenter();

		if (!getParams())
		{
			removeListeners(imp);
			return;
		}

		try
		{
			calculateRadialProfile();
		}
		catch (Exception e)
		{
			IJ.error(e.getMessage());
		}

		if (IJ.macroRunning())
			Functions.registerExtensions(this);

		imp.setOverlay(null);
		removeListeners(imp);
	}

	private void removeListeners(ImagePlus imp)
	{
		ImageWindow win = imp.getWindow();
		if (win == null) return;
		win.removeKeyListener(this);
		canvas = win.getCanvas();
		canvas.removeKeyListener(this);
		canvas.removeMouseMotionListener(this);
		canvas.removeMouseWheelListener(this);
		Prefs.requireControlKey = previousRequireControlKeyState;
		instance = null;
	}

	public void imageOpened(ImagePlus imp)
	{
	}
	
	public void imageUpdated(ImagePlus imp)
	{
	}

	public void imageClosed(ImagePlus imp)
	{
		if (imp == this.imp)
		{
			removeListeners(imp);
			gd.dispose();
		}
	}

	private NonBlockingGenericDialog getDialog()
	{
		return gd;
	}


	private ImagePlus getImagePlus()
	{
		return imp;
	}

	public void setXYcenter()
	{
		if(imp.getRoi() == null)
		{
			X0 = canvas. getWidth() / 2.0;
			Y0 = canvas.getHeight() / 2.0;
			mR = (X0 + Y0) / 2.0;
		}
		else
		{
			rct = imp.getRoi().getBounds();
			X0 = (double) rct.x + (double) rct.width / 2;
			Y0 = (double) rct.y + (double) rct.height / 2;
			mR = (rct.width + rct.height) / 4.0;
		}
		Sa = 0;
		Ia = 180;
	}

	private void correctValues()
	{
		if(mR < 0)
		{
			mR = -mR;
			numericFields[RADIUS].setText(IJ.d2s(mR, 2));
		}
		if(Ia < 0)
		{
			Ia = -Ia;
			numericFields[INT_ANGLE].setText(IJ.d2s(Ia, 0));
		}
		if(Ia > 180)
		{
			Ia = Ia%180;
			numericFields[INT_ANGLE].setText(IJ.d2s(Ia, 0));
		}
	}

	public double cos(double val)
	{
		if(Math.IEEEremainder(val, 2.0 * Math.PI) == 0.0)
			return 1.0;
		else if(Math.IEEEremainder(val, Math.PI) == 0.0)
			return -1.0;
		else if(Math.IEEEremainder(val, Math.PI / 2.0) == 0.0)
			return 0.0;
		else
			return Math.cos(val);
	}

	public double sin(double val)
	{
		return(Math.IEEEremainder(val, Math.PI) == 0.0)?0.0:Math.sin(val);
	}

	private void setCosSin()
	{
		int i;
		double tmpVal;
		cosMin = cos(Math.PI * (Sa - Ia) / 180.0);
		cosMax = cosMin;
		sinMin = sin(Math.PI * (Sa - Ia) / 180.0);
		sinMax = sinMin;
		for(i = 1; i <= 2 * Ia; i++)
		{
			tmpVal = cos(Math.PI * (Sa - Ia + i) / 180);
			if(tmpVal > cosMax)
				cosMax = tmpVal;
			else if(tmpVal < cosMin)
				cosMin = tmpVal;
			tmpVal = sin(Math.PI * (Sa - Ia + i) / 180);
			if(tmpVal > sinMax)
				sinMax = tmpVal;
			else if(tmpVal < sinMin)
				sinMin = tmpVal;
		}
	}

	public int colorX(int val)
	{
		return(Math.IEEEremainder((double) (val - 1), 4.0) == 0.0)?1:0;
	}

	public int colorY(int val)
	{
		return(Math.IEEEremainder((double) (val - 2), 4.0) == 0.0)?1:0;
	}

	public int colorZ(int val)
	{
		return(Math.IEEEremainder((double) (val - 3), 4.0) == 0.0)?1:0;
	}

	public void doRadialDistribution(ImageProcessor ip)
	{
//		nBins = (int) (3 * mR / 4);
		nBins = (int) (Math.floor(mR));
		dataX  = new float   [nBins];
		dataY  = new float[1][nBins];
	        String[] headings = new String[2];
		int i;
		int thisBin;
		double a, b;
		double R;
		double xmin = X0 - mR, xmax = X0 + mR, ymin = Y0 - mR, ymax = Y0 + mR;
		setCosSin();
		for (a = xmin; a <= xmax; a++)
		{
			for (b = ymin; b <= ymax; b++)
			{
				R = Math.sqrt((a - X0) * (a - X0) + (b - Y0) * (b - Y0));
				if( (a - X0) / R >= cosMin && (a - X0) / R <= cosMax )
				{
					if( (Y0 - b) / R >= sinMin && (Y0 - b) / R <= sinMax )
					{
						thisBin = (int) Math.floor((R/mR) * (double) nBins);
						if (thisBin == 0) thisBin = 1;
						thisBin = thisBin - 1;
//						if (thisBin > nBins - 1) thisBin = nBins - 1;			// correction suggested by Kota Miura
						if (thisBin < nBins)
						{
							dataX   [thisBin] = dataX   [thisBin] + 1;
							dataY[0][thisBin] = dataY[0][thisBin] + ip.getPixelValue((int) a, (int) b);
						}
					}
				}
			}
		}
		Calibration cal = imp.getCalibration();
		if (cal == null || cal.getUnit() == "pixel" || cal.pixelWidth != cal.pixelHeight)
			useCalibration = false;

		if (useCalibration)
		{
			for (i = 0; i < nBins; i++)
			{
				dataY[0][i] = dataY[0][i] / dataX[i];
				dataX[i]    = (float) (cal.pixelWidth * mR * ((double)(i + 1) / nBins));
			}
			plot = new MultyPlotExt(getImageTitle(), "Radius ["+cal.getUnits()+"]", "Normalized Integrated Intensity",  dataX, dataY[0]);
			headings[0] = "Radius [" + cal.getUnits() + "]";
		}
		else
		{
			for (i = 0; i < nBins; i++)
			{
				dataY[0][i] = dataY[0][i] / dataX[i];
				dataX[i]    = (float) (mR * ((double) (i + 1) / nBins));
			}
			plot = new MultyPlotExt(getImageTitle(), "Radius [pixels]", "Normalized Integrated Intensity",  dataX, dataY[0]);
			headings[0] = "Radius [pixels]\t";
		}
		headings[1] = "Normalized Integrated Intensity";
	        MultyPlotWindowExt wnd = plot.show();
        	wnd.setLineHeadings(headings, false);
        writeToFile(dataX, dataY[0], nBins);
	}

    public void writeToFile(float dataX[], float dataY[], int nBins)
    {
        String fileName = getImageTitle()+dataX[0]+" "+dataY[0]+".txt";
        Boolean ap = false;
        File file = new File(fileName);
        FileWriter fr = null;
        try {
            fr = new FileWriter(file,ap );
            int i;
            for (i = 0; i < nBins; i++)
			{
                fr.write(dataX[i] + " " + dataY[i] + " \n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }finally{
            //close resources
            try {
                fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


	public void doStackRadialDistribution()
	{
//		nBins = (int) (3 * mR / 4);
		nBins = (int) (Math.floor(mR));
		dataX  = new float[nBins];
		dataY  = new float[imp.getStackSize()][nBins];
		int i, j;
		int thisBin;
	        String[] headings = new String[imp.getStackSize() + 1];
		float minY, maxY;
		double[] extrema;
		double a, b;
		double R;
		double xmin = X0 - mR, xmax = X0 + mR, ymin = Y0 - mR, ymax = Y0 + mR;
		setCosSin();
		Calibration cal = imp.getCalibration();
		if (cal == null || cal.getUnit() == "pixel" || cal.pixelWidth != cal.pixelHeight)
			useCalibration = false;

		for (j = 0; j != imp.getStackSize(); j++)
		{
			imp.setSlice(j + 1);
			headings[j+1] =  String.valueOf(j);
			for (a = xmin; a <= xmax; a++)
			{
				for (b = ymin; b <= ymax; b++)
				{
					R = Math.sqrt((a - X0) * (a - X0) + (b - Y0) * (b - Y0));
					if( (a - X0) / R >= cosMin && (a - X0) / R <= cosMax )
					{
						if( (Y0 - b) / R >= sinMin && (Y0 - b) / R <= sinMax )
						{
							thisBin = (int) Math.floor((R / mR) * (double) nBins);
							if (thisBin == 0) thisBin = 1;
							thisBin = thisBin - 1;
//							if (thisBin > nBins - 1) thisBin = nBins - 1;			// correction suggested by Kota Miura
							if (thisBin < nBins)
							{
								dataX   [thisBin] = dataX   [thisBin] + 1;
								dataY[j][thisBin] = dataY[j][thisBin] + ip2.getPixelValue((int) a, (int) b);
							}
						}
					}
				}
			}
			for (i = 0; i < nBins; i++)
			{
				dataY[j][i] =  dataY[j][i] / dataX[i];
				dataX[i] = 0;
			}
		}
		minY = dataY[0][0];
		maxY = dataY[0][0];
		for (j = 0; j != imp.getStackSize(); j++)
		{
			extrema = Tools.getMinMax(dataY[j]);
			if (extrema[0] < minY)
				minY = (float) extrema[0];
			if (extrema[1] > maxY)
				maxY = (float) extrema[1];
		}
		if (useCalibration)
		{
			for (i = 0; i < nBins; i++)
				dataX[i] = (float) (cal.pixelWidth * mR * ((double)(i + 1) / nBins));
//			plot = new MultyPlotExt("Radial Profile Plot", "Radius ["+cal.getUnits()+"]", "Normalized Integrated Intensity", dataX, minY, maxY);
			plot = new MultyPlotExt("Radial Profile Plot", "Radius ["+cal.getUnits()+"]", "Normalized Integrated Intensity", dataX, dataY[0]);
			headings[0] = "Radius ["+cal.getUnits()+"]";
		}
		else
		{
			for (i = 0; i < nBins; i++)
				dataX[i] = (float) (mR * ((double) (i + 1) / nBins));
//			plot = new MultyPlotExt("Radial Profile Plot", "Radius [pixels]", "Normalized Integrated Intensity", dataX, minY, maxY);
			plot = new MultyPlotExt("Radial Profile Plot", "Radius [pixels]", "Normalized Integrated Intensity", dataX, dataY[0]);
			headings[0] = "Radius [pixels]";
		}
		plot.setLimits(dataX[0], dataX[nBins - 1], minY, maxY);
		for (j = 1; j != imp.getStackSize(); j++)
		{
			plot.setColor(new Color(colorX(j) * 0xff, colorY(j) * 0xff, colorZ(j) * 0xff));
			plot.addPoints(dataX, dataY[j], 2);
		}
		plot.setColor(new Color(0, 0, 0));		// This line is added so that dataY[0] which is actually drawn at last (for I don't know which reason - maybe a bug of the MultyPlotExt class) is drawn in black color (as expected from the code) and not with the last defined color of the previous loop
		MultyPlotWindowExt wnd = plot.show();
		wnd.setLineHeadings(headings, false);
//		wnd.setPrecision(3,3);
	}

	public String getImageTitle()
	{
		String str = imp.getTitle();
		int len = str.length();
		if (len > 4 && str.charAt(len - 4) == '.' && !Character.isDigit(str.charAt(len - 1)))
			return str.substring(0, len - 4);  
		else
			return str;
	}

	public void calculateRoiRadius(ImageProcessor ip)
	{
		String title;
		Calibration cal = imp.getCalibration();
		int measurements = Analyzer.getMeasurements();			// defined in Set Measurements dialog
		Analyzer.setMeasurements(measurements);
		Analyzer a = new Analyzer();
		ImageStatistics stats = imp.getStatistics(measurements);
		title = getImageTitle();
		a.saveResults(stats, imp.getRoi());				// store in system results table
		ResultsTable rt = Analyzer.getResultsTable();			// get the system results table
		rt.addLabel("Label", title);
		if (useCalibration)
		{
			rt.addValue("Radius [" + cal.getUnits() + "]", cal.pixelWidth * mR);
			rt.addValue("Radius [pixels]", mR);
		}
		else
			rt.addValue("Radius [pixels]", mR);
		a.displayResults();						// display the results in the worksheet
		a.updateHeadings();						// update the worksheet headings
	}

	public void setParams(double X_Center, double Y_Center, double Radius, int Start_Angle, int Int_Angle, boolean Use_Calibration, boolean Make_Stack_Analysis)
	{
 		X0			= X_Center;
 		Y0			= Y_Center;
 		mR			= Radius;
 		Sa			= Start_Angle;
 		Ia			=   Int_Angle;
		useCalibration		= Use_Calibration;
		makeStackAnalysis	= Make_Stack_Analysis;
	}

	public void doRadialProfile(double X_Center, double Y_Center, double Radius, int Start_Angle, int Int_Angle, boolean Use_Calibration, boolean Make_Stack_Analysis)
	{
		setParams(X_Center, Y_Center, Radius, Start_Angle, Int_Angle, Use_Calibration, Make_Stack_Analysis);
 		calculateRadialProfile();
	}


	private ExtensionDescriptor[] extensions =
	{
		ExtensionDescriptor.newDescriptor("getXValue"	, this, ARG_NUMBER),
		ExtensionDescriptor.newDescriptor("getYValue"	, this, ARG_NUMBER, ARG_NUMBER),
		ExtensionDescriptor.newDescriptor("getBinSize"	, this),
		ExtensionDescriptor.newDescriptor("getStackSize", this),
	};

	public ExtensionDescriptor[] getExtensionFunctions()
	{
		return extensions;
	}

	public String handleExtension(String name, Object[] args)
	{
		if (name.equals("getXValue"))
		{
			int pos = ( (Double) args[0] ).intValue();
			return Double.toString(dataX[pos]);
		}
		else if (name.equals("getYValue"))
		{
			int pos0 = ( (Double) args[0] ).intValue();
			int pos1 = ( (Double) args[1] ).intValue();
			return Double.toString(dataY[pos0][pos1]);
		}
		else if (name.equals("getBinSize"))
		{
			return Integer.toString(nBins);
		}
		else if (name.equals("getStackSize"))
		{
			return Integer.toString(imp.getStackSize());
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private boolean getParams()
	{
		gd = new NonBlockingGenericDialog("Radial Profile Angle on [" + imp.getWindow().getTitle() + "]");
		gd.addNumericField	("X_center (pixels):"           , X0, 2);
		gd.addNumericField	("Y_center (pixels):"           , Y0, 2);
		gd.addNumericField	("Radius (pixels):  "           , mR, 2);
		gd.addNumericField	("Starting_angle (\u00B0):     ", Sa, 0);
		gd.addNumericField	("Integration_angle (\u00B0):"  , Ia, 0);
		gd.addPanel		(addPanel());
		gd.addRadioButtonGroup	(null, items, 2, 1, "Plot integration angle");
		gd.addCheckbox		("Use_Spatial_Calibration", useCalibration);
		gd.addCheckbox		("Calculate_Radial_Profile_on_Stack", makeStackAnalysis);
		gd.setOKLabel		("Calculate Radial Profile and Exit");
		gd.addHelp		("http://rsbweb.nih.gov/ij/plugins/radial-profile-ext.html");

		numericFields = (TextField[]) (gd.getNumericFields().toArray(new TextField[gd.getNumericFields().size()]));

		Vector radioButtonGroups = gd.getRadioButtonGroups();
		cbg = (CheckboxGroup)(radioButtonGroups.elementAt(0));

		Vector checkboxs = gd.getCheckboxes();
		cb0 = (Checkbox)(checkboxs.elementAt(0));
		cb1 = (Checkbox)(checkboxs.elementAt(1));

		plotROI();
		gd.addDialogListener(this);
		gd.addMouseWheelListener(this);
		gd.showDialog();

		if (gd.wasCanceled())
		{
			imp.setOverlay(null);
			return false;
		}

		return true;
	}

	private Panel addPanel()
	{
		Panel panel = new Panel();
		panel.setLayout(new GridLayout(2, 1));
		button0 = new Button("Calculate ROI Radius (g)");
		button0.addActionListener(this);
		panel.add(button0);
		button1 = new Button("Calculate Radial Profile (q)");
		button1.addActionListener(this);
		panel.add(button1);

		return panel;
	}

	private void plotROI()
	{
		if(cbg.getSelectedCheckbox().getLabel() == "Plot Droplet with integration angle")
			plotDropletAndIntegrationROI();
		else
			plotIntegrationROI();
	}

	private void plotDropletAndIntegrationROI()
	{		
		imp.setOverlay(null);

		for(int i = -1; i <= 1; i = i + 2)
		{
			xPoint[i + 1] = (int) (X0 + mR * cos(Math.PI * (Sa + i * Ia) / 180));
			yPoint[i + 1] = (int) (Y0 - mR * sin(Math.PI * (Sa + i * Ia) / 180));
		}
		xPoint[1] = (int) X0;
		yPoint[1] = (int) Y0;

		overlay = new Overlay(new PolygonRoi(xPoint, yPoint, 3,  Roi.ANGLE));
		overlay.addElement(new OvalRoi((int)(X0 - mR), (int)(Y0 - mR), (int)(2 * mR), (int)(2 * mR)));
		imp.setOverlay(overlay);
		s2 = new ShapeRoi (new OvalRoi((int)(X0 - mR), (int)(Y0 - mR), (int)(2 * mR), (int)(2 * mR)));
		imp.setRoi(s2);
		imp.repaintWindow();
	}

	private void plotIntegrationROI()
	{
		imp.setOverlay(null);

		for(int i = -2; i <= 2; i++)
		{
			xPoint[i+2] = (int) (X0 + 2 * mR * cos(Math.PI * (Sa + 0.5 * i * Ia) / 180));
			yPoint[i+2] = (int) (Y0 - 2 * mR * sin(Math.PI * (Sa + 0.5 * i * Ia) / 180));
		}
		xPoint[5] = (int) X0;
		yPoint[5] = (int) Y0;
		
		s1 = new ShapeRoi(new PolygonRoi(xPoint, yPoint, 6,  Roi.POLYGON));
		s2 = new ShapeRoi(new OvalRoi((int)(X0 - mR), (int)(Y0 - mR), (int)(2 * mR), (int)(2 * mR)));
		s1.and(s2);
		imp.setRoi(s1);
		imp.repaintWindow();
	}

	private void calculateROIRadius()
	{
		useCalibration = cb0.getState();
		calculateRoiRadius(ip2);
	}
	
	public void calculateRadialProfile()
	{
		makeStackAnalysis = cb1.getState();
		if(makeStackAnalysis)
		{
			if (imp.getStackSize() > 1)
			{
				useCalibration = cb0.getState();
				doStackRadialDistribution();
			}
			else
				IJ.showMessage("Error", "Stack required");
		}
		else
		{
			useCalibration = cb0.getState();
			doRadialDistribution(ip2);
		}
	}

	public void actionPerformed(ActionEvent e)
	{
		Object b = e.getSource();

		if (b == button0)
			calculateROIRadius();
		else if (b == button1)
			calculateRadialProfile();
	}

	public void keyPressed(KeyEvent e)
	{
		int keyCode = e.getKeyCode();
		int flags   = e.getModifiers();
		e.consume();

		if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_NUMPAD6)
		{ 
			if (flags == KeyEvent.SHIFT_MASK)
				X0 += 5;
			else if (flags == KeyEvent.CTRL_MASK)
				X0 += 10;
			else
				X0++;
			numericFields[X_CENTER].setText(IJ.d2s(X0, 2));
			imp.repaintWindow();
			plotROI();
		}
		else if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_NUMPAD4)
		{ 
			if (flags == KeyEvent.SHIFT_MASK)
				X0 -= 5;
			else if (flags == KeyEvent.CTRL_MASK)
				X0 -= 10;
			else
				X0--;
			numericFields[X_CENTER].setText(IJ.d2s(X0, 2));
			imp.repaintWindow();
			plotROI();
		}
		else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_NUMPAD2)
		{ 
			if (flags == KeyEvent.SHIFT_MASK)
				Y0 += 5;
			else if (flags == KeyEvent.CTRL_MASK)
				Y0 += 10;
			else
				Y0++;
			numericFields[Y_CENTER].setText(IJ.d2s(Y0, 2));
			imp.repaintWindow();
			plotROI();			
		}
		else if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_NUMPAD8)
		{ 
			if (flags == KeyEvent.SHIFT_MASK)
				Y0 -= 5;
			else if (flags == KeyEvent.CTRL_MASK)
				Y0 -= 10;
			else
				Y0--;
			numericFields[Y_CENTER].setText(IJ.d2s(Y0, 2));
			imp.repaintWindow();
			plotROI();		
		}
		else if (keyCode== KeyEvent.VK_PAGE_UP || keyCode==e.VK_ADD)
		{
			if((e.getModifiersEx() & alt_mask) == alt_mask)
			{
				if((e.getModifiersEx() & alt_ctrl_mask) == alt_ctrl_mask)
					Ia += 10;
				else if((e.getModifiersEx() & alt_shift_mask) == alt_shift_mask)
					Ia += 5;
				else
					Ia ++;
				numericFields[INT_ANGLE].setText(IJ.d2s(Ia, 0));
			}
			else
			{
				if(flags == KeyEvent.CTRL_MASK)
					mR += 10;
				else if(flags == KeyEvent.SHIFT_MASK)
					mR += 5;
				else
					mR ++;
				numericFields[RADIUS].setText(IJ.d2s(mR, 2));
			}
			imp.repaintWindow();
			plotROI();
		}
		else if (keyCode == KeyEvent.VK_PAGE_DOWN || keyCode == e.VK_SUBTRACT)
		{
			if((e.getModifiersEx() & alt_mask) == alt_mask)
			{
				if((e.getModifiersEx() & alt_ctrl_mask) == alt_ctrl_mask)
					Ia -= 10;
				else if((e.getModifiersEx() & alt_shift_mask) == alt_shift_mask)
					Ia -= 5;
				else
					Ia --;
				numericFields[INT_ANGLE].setText(IJ.d2s(Ia, 0));
			}
			else
			{
				if(flags == KeyEvent.CTRL_MASK)
					mR = (mR >= 10) ? (mR - 10) : mR;
				else if(flags == KeyEvent.SHIFT_MASK)
					mR = (mR >=  5) ? (mR -  5) : mR;
				else
					mR = (mR >=  1) ? (mR -  1) : mR;
				numericFields[RADIUS].setText(IJ.d2s(mR, 2));
			}
			imp.repaintWindow();
			plotROI();
		}
		else if (keyCode == e.VK_G)
			calculateROIRadius();
		else if (keyCode == e.VK_Q)
			calculateRadialProfile();
	}

	public void keyReleased(KeyEvent e)
	{
	}

	public void keyTyped(KeyEvent e)
	{
	}

	public boolean dialogItemChanged(GenericDialog gd, AWTEvent e)
	{	
		X0					=		gd.getNextNumber();
		Y0					=		gd.getNextNumber();
		mR					=		gd.getNextNumber();
		Sa					= (int)	gd.getNextNumber();
		Ia					= (int)	gd.getNextNumber();
		useCalibration		=		gd.getNextBoolean();
		makeStackAnalysis	=		gd.getNextBoolean();

		if(gd.invalidNumber())
		{
			IJ.beep();
			return false;
		}
		correctValues();
		plotROI();
		return true;
	}

	public void mouseMoved(MouseEvent e) { }

	public void mouseDragged(MouseEvent e)
	{
		X0 = canvas.offScreenX(e.getX());
		Y0 = canvas.offScreenY(e.getY());
		numericFields[X_CENTER].setText(IJ.d2s(X0, 2));
		numericFields[Y_CENTER].setText(IJ.d2s(Y0, 2));
		imp.repaintWindow();
		plotROI();
	}

	public void mouseWheelMoved(MouseWheelEvent e)
	{
		e.consume();

		for (int i = 0; i < numericFields.length; i++)			// mouseWheelEvents for numeric input fields
		{
			if(numericFields[i].isFocusOwner())
			{
				mouseWheelOnNumericField(e, i);
				return;						// The MouseWheelEvent was used, no more action
			}
		}
		if (IJ.altKeyDown())						// mouseWheelEvents not for a numeric field: modify the radius or angle
			mouseWheelOnNumericField(e, INT_ANGLE);
		else
			mouseWheelOnNumericField(e, RADIUS);
	}

	void mouseWheelOnNumericField(MouseWheelEvent e, int fieldIndex)
	{
		double value = Tools.parseDouble(numericFields[fieldIndex].getText());
		if (Double.isNaN(value))
			return;							// invalid number, can't increment/decrement
		int step = 1;
		if ((e.getModifiersEx() & (shift_button_mask | ctrl_mask)) == ctrl_mask)
			step = 10;
		else if (IJ.shiftKeyDown())
			step = 5;
		value -= step * e.getWheelRotation();
		if(fieldIndex <= 2)
			numericFields[fieldIndex].setText(IJ.d2s(value, 2));
		else
			numericFields[fieldIndex].setText(IJ.d2s(value, 0));
		return;
	}

	public void showAbout()
	{
		IJ.showMessage("Radial Profile Angle...",
			"This plugin is an update of the Radial Profile plugin from Paul Baggethun:\n" +
			"http://rsb.info.nih.gov/ij/plugins/radial-profile.html.\n" +
			"The plugin lets you choose the starting angle and integration angle over\n" +
			"which the integration on the defined Roi is done.\n" +
			"The integration will be done over an area defined by :\n" +
			"starting angle +/- integration angle.\n" +
			"The size and the position of the Roi can be defined and modified by\n" +
			"either using the plugin menu, the mouse or shortkeys on keyboard.\n" +
			"Additionnally, the integration calculation can be done over a whole stack.\n" +
			"                                                                                                                                               \n" +
			"The plugin is implementing the MultyPlotWindow developped in the Color_Profiler\n" +
			"routine by Dimiter Prodanov (University of Leiden).\n" +
			"                                                                                                                                               \n" +
			"The plugin includes also a routine abling the calculation of the radius\n" +
			"of the generated Roi and saving the data in a result panel.\n" +
			"                                                                                                                                               \n" +
			"The new version implements macro recording and excecution features.\n" +
			"And following several discussions and advices with Michael Schmid\n" +
			"(schmid@iap.tuwien.ac.at) the ROI position and size can be modified\n" +
			"with the mouse and the opening angle cone is always drawn together\n" +
			"with the round ROI.r\n" +
			"                                                                                                                                              \n" +
			"Finally the plugin takes also into account a bug correction introduced\n" +
			"by Kota Miura (miura@embl.de).\n" +
			"                                                                                                                                               \n" +
			"First version: 4-20-2005\n\n" +
			"Last updated: 1-14-2014  (requires: ImageJ 1.38p or higher)\n\n" +
			"Author : Philippe Carl (philippe.carl@unistra.fr)"
		);
	}

}