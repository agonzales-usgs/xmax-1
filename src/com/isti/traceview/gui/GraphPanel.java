package com.isti.traceview.gui;

import java.net.URL;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.RepaintManager;
import javax.swing.SwingConstants;
import javax.swing.border.EtchedBorder;
import javax.swing.event.MouseInputListener;

import org.apache.log4j.Logger;
import org.jfree.chart.axis.DateAxis;
import org.jfree.ui.RectangleEdge;

import com.isti.traceview.CommandExecutor;
import com.isti.traceview.ITimeRangeAdapter;
import com.isti.traceview.TraceView;
import com.isti.traceview.common.IEvent;
import com.isti.traceview.common.TimeInterval;
import com.isti.traceview.common.UniqueList;
import com.isti.traceview.data.PlotDataProvider;
import com.isti.traceview.data.Segment;
import com.isti.traceview.processing.IFilter;
import com.isti.traceview.processing.Rotation;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.TimeZone;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.awt.image.MemoryImageSource;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.io.IOException;

/**
 * This is graphics container; it contains a list of ChannelView(s) (panels) and renders them as a
 * 1-column table; responsible for ChannelView selecting and ordering selecting time and values
 * ranges, holds information about current representation state.
 * 
 * @author Max Kokoulin
 */
public class GraphPanel extends JPanel implements Printable, MouseInputListener, Observer {
	private static Logger lg = Logger.getLogger(GraphPanel.class); // @jve:decl-index=0:

	private static final Color selectionColor = Color.YELLOW;
	private static Font axisFont = null; // @jve:decl-index=0:
	private static Cursor hiddenCursor = null;
	private static Cursor crossCursor = new Cursor(Cursor.CROSSHAIR_CURSOR);

	static {
		int[] pixels = new int[16 * 16];
		Image image = Toolkit.getDefaultToolkit().createImage(new MemoryImageSource(16, 16, pixels, 0, 16));
		hiddenCursor = Toolkit.getDefaultToolkit().createCustomCursor(image, new Point(0, 0), "HIDDEN_CURSOR");
	}

	/**
	 * Time interval including currently loaded in GraphPanel channels set
	 */
	private TimeInterval timeRange = new TimeInterval();

	/**
	 * Maximum Y scale value for manual scale mode
	 * 
	 * @uml.property name="manualValueMax"
	 */
	private int manualValueMax = Integer.MIN_VALUE;

	/**
	 * Minimum Y scale value for manual scale mode
	 * 
	 * @uml.property name="manualValueMin"
	 */
	private int manualValueMin = Integer.MAX_VALUE;

	/**
	 * List of graphs
	 * 
	 * @uml.property name="channelShowSet"
	 */
	private List<ChannelView> channelShowSet = null; // @jve:decl-index=0:
	private List<ChannelView> selectedChannelShowSet = null;

	/**
	 * @uml.property name="unitsShowCount" Amount of units to show simultaneously in this graph
	 *               panel
	 */
	private int unitsShowCount;

	private DrawAreaPanel drawAreaPanel = null;
	private SouthPanel southPanel = null;

	private boolean mouseSelectionEnabled = true;

	/**
	 * Current mouse position, X coordinate. Used by repaint()
	 */
	protected int mouseX;

	/**
	 * Current mouse position, Y coordinate. Used by repaint()
	 */
	protected int mouseY;

	/**
	 * Mouse position during previous repaint() call, X coordinate.
	 */
	protected int previousMouseX = -1;

	/**
	 * Mouse position during previous repaint() call, Y coordinate.
	 */
	protected int previousMouseY = -1;

	/**
	 * Flag if we need to repaint mouse cross cursor
	 */
	private boolean mouseRepaint = false;

	/**
	 * Flag if we need to force repaint mouse cross cursor, in spite of mouseRepaint value.
	 */
	private boolean forceRepaint = false;

	/**
	 * Mouse button was pressed, X coordinate.
	 */
	protected int mousePressX;

	/**
	 * Mouse button was pressed, Y coordinate.
	 */
	protected int mousePressY;

	/**
	 * Mouse button which was pressed
	 */
	protected int button = MouseEvent.NOBUTTON;

	private long selectedAreaXbegin = Long.MAX_VALUE;
	private long selectedAreaXend = Long.MIN_VALUE;
	private double selectedAreaYbegin = Double.NaN;
	private double selectedAreaYend = Double.NaN;

	private long previousSelectedAreaXbegin = Long.MAX_VALUE;
	private long previousSelectedAreaXend = Long.MIN_VALUE;
	private double previousSelectedAreaYbegin = Double.NaN;
	private double previousSelectedAreaYend = Double.NaN;
	private boolean paintNow = false;

	/**
	 * X coordinate of last clicked point, to compute time differences between last two clicks.
	 */
	private int mouseClickX = -1;

	private GraphPanelObservable observable = null;

	private boolean showBigCursor = false;

	/**
	 * Flag if graphPanel should manage its TimeInterval itself or use given time interval
	 */
	private boolean shouldManageTimeRange = true;

	/**
	 * @uml.property name="scaleMode"
	 */
	private IScaleModeState scaleMode = null; // @jve:decl-index=0:

	/**
	 * @uml.property name="colorMode"
	 */
	private IColorModeState colorMode = null; // @jve:decl-index=0:

	/**
	 * @uml.property name="meanState"
	 */
	private IMeanState meanState; // @jve:decl-index=0:

	/**
	 * @uml.property name="meanState"
	 */
	private IOffsetState offsetState; // @jve:decl-index=0:

	/**
	 * @uml.property name="phaseState"
	 */
	private boolean phaseState = false;

	private boolean pickState = false;

	private boolean overlay = false;

	private boolean select = false;

	private IFilter filter = null;

	private Rotation rotation = null;

	/**
	 * Visible earthquakes to draw on the graphs
	 */

	private Set<IEvent> selectedEarthquakes = null; // @jve:decl-index=0:
	/**
	 * Visible phases to draw on the graphs
	 */

	private Set<String> selectedPhases = null; // @jve:decl-index=0:

	private IMouseAdapter mouseAdapter = null;
	private ITimeRangeAdapter timeRangeAdapter = null;
	protected IChannelViewFactory channelViewFactory = new DefaultChannelViewFactory();
	private Image markPositionImage = null;
	/**
	 * if we need show block header as tooltip
	 */
	private boolean isShowBlockHeader = false;

	/**
	 * Default constructor
	 */
	public GraphPanel() {
		this(true);
	}
	
	public GraphPanel(boolean showTimePanel) {
		super();
		initialize(showTimePanel);
		addMouseListener(this);
		addMouseMotionListener(this);
		selectedEarthquakes = new HashSet<IEvent>();
		selectedPhases = new HashSet<String>();

		meanState = new MeanModeDisabled();
		colorMode = new ColorModeBySegment();
		scaleMode = new ScaleModeAuto();
		offsetState = new OffsetModeDisabled();
		observable = new GraphPanelObservable();
		mouseSelectionEnabled = true;
	}

	/**
	 * This method initializes graph panel
	 */
	private void initialize(boolean showTimePanel) {
		if (axisFont == null) {
			axisFont = new Font(getFont().getName(), getFont().getStyle(), 10);
		}
		channelShowSet = Collections.synchronizedList(new ArrayList<ChannelView>());
		unitsShowCount = TraceView.getConfiguration().getUnitsInFrame();
		setBorder(BorderFactory.createEtchedBorder(EtchedBorder.RAISED));
		this.setLayout(new BorderLayout());
		this.add(getDrawAreaPanel(), BorderLayout.CENTER);
		this.add(getSouthPanel(showTimePanel), BorderLayout.SOUTH);

/** ----------------- MTH ---------------- **/
//  file defaultMarkPosition.gif will NOT be found this way as java will
//  look for build/com/isti/traceview/gui/defaultMarkPosition.gif and will NOT find the
//  resource, but at least it won't crash
URL url = null;
  try {
     URL baseUrl = GraphPanel.class.getResource(".");
     if (baseUrl != null) {
        url = new URL(baseUrl, "defaultMarkPosition.gif");
     } else {
        url = GraphPanel.class.getResource("defaultMarkPosition.gif");
     }
System.out.format("== MTH: file=%s path=%s\n", url.getFile(), url.getPath() );
     markPositionImage = javax.imageio.ImageIO.read(url);
  //} catch (MalformedURLException e) {
  } catch (Exception e) {
     // Do something appropriate
  }

/** ----------------- MTH ---------------- **/

/**
		try {
			markPositionImage = javax.imageio.ImageIO.read(ClassLoader.getSystemResource("defaultMarkPosition.gif"));
		} catch (IOException e) {
			markPositionImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			lg.error("Can't read mark position image: " + e);
		}
**/
	}
	
	public void forceRepaint(){
		forceRepaint = true;
		repaint();
	}

	/**
	 * Sets factory to produce ChannelViews. Library user can define his own factory to produce
	 * customized ChannelViews.
	 * 
	 * @param cvf
	 *            User's factory
	 */
	public void setChannelViewFactory(IChannelViewFactory cvf) {
		this.channelViewFactory = cvf;
	}

	/**
	 * Getter of time range
	 * 
	 * @return currently time range
	 */
	public TimeInterval getTimeRange() {
		return timeRange;
	}

	/**
	 * Time range setter
	 * 
	 * @param timeRange
	 *            time range to set. Graph panel redraws to show this time range.
	 */
	public void setTimeRange(TimeInterval timeRange) {
		lg.debug("GraphPanel.setTimeRange " + timeRange);
		this.timeRange = timeRange;
		if (timeRangeAdapter != null && TraceView.getFrame() != null) {
			timeRangeAdapter.setTimeRange(timeRange);
		}
		southPanel.getAxisPanel().setTimeRange(timeRange);
		mouseClickX = -1;
		southPanel.getInfoPanel().update(timeRange);
		observable.setChanged();
		observable.notifyObservers(timeRange);
		forceRepaint();
	}

	/**
	 * If true, graph panel itself changes time range after data set changing to show all loaded
	 * data. If false, given time range used.
	 * 
	 * @param value
	 *            Flag if graphPanel should manage its TimeInterval itself
	 */
	public void setShouldManageTimeRange(boolean value) {
		shouldManageTimeRange = value;
	}

	/**
	 * If true, graph panel itself changes time range after data set changing to show all loaded
	 * data. If false, given time range used.
	 * 
	 * @return Flag if graphPanel should manage its TimeInterval itself
	 */
	public boolean isShouldManageTimeRange() {
		return shouldManageTimeRange;
	}

	/**
	 * If true, graph panel selects blue area while mouse dragging and calls time range or value
	 * range changing after mouse releasing
	 * 
	 * @return Flag if mouse selection enabled
	 */
	public boolean isMouseSelectionEnabled() {
		return mouseSelectionEnabled;
	}

	/**
	 * If true, graph panel selects blue area while mouse dragging and calls time range or value
	 * range changing after mouse releasing
	 * 
	 * @param mouseSelectionEnabled
	 *            Flag if mouse selection enabled
	 */
	public void setMouseSelectionEnabled(boolean mouseSelectionEnabled) {
		this.mouseSelectionEnabled = mouseSelectionEnabled;
	}

	/**
	 * Sets X coordinates of selected rectangle
	 * 
	 * @param begin
	 *            left edge position
	 * @param end
	 *            right edge position
	 */
	public void setSelectionX(long begin, long end) {
		// lg.debug("Sel X: " + begin + "-" + end);
		selectedAreaXbegin = begin;
		selectedAreaXend = end;
	}

	/**
	 * Sets Y coordinates of selected rectangle
	 * 
	 * @param begin
	 *            top edge position
	 * @param end
	 *            bottom edge position
	 */
	public void setSelectionY(double begin, double end) {
		// lg.debug("Sel Y: " + begin + "-" + end);
		selectedAreaYbegin = begin;
		selectedAreaYend = end;
	}

	/**
	 * Sets mouse adapter which defines behavior after mouse operations
	 */
	public void setMouseAdapter(IMouseAdapter ma) {
		mouseAdapter = ma;
	}

	/**
	 * Removes mouse adapter which defines behavior after mouse operations
	 */
	public void removeMouseAdapter() {
		mouseAdapter = null;
	}

	/**
	 * Sets time range adapter which defines behavior after setting time range
	 */
	public void setTimeRangeAdapter(ITimeRangeAdapter tr) {
		timeRangeAdapter = tr;
	}

	/**
	 * Removes time range adapter which defines behavior after setting time range
	 */
	public void removeTimeRangeAdapter() {
		timeRangeAdapter = null;
	}

	/**
	 * Getter of the property <tt>manualValueMin</tt>
	 * 
	 * @return Minimum of value axis range, it used in XHair scaling mode
	 */
	public double getManualValueMin() {
		return ScaleModeAbstract.getManualValueMin();
	}

	/**
	 * Setter of the property <tt>manualValueMin</tt>
	 * 
	 * @param manualValueMin
	 *            Minimum of value axis range, it used in XHair scaling mode
	 */
	public void setManualValueMin(double manualValueMin) {
		ScaleModeAbstract.setManualValueMin(manualValueMin);
		forceRepaint();
	}

	/**
	 * Getter of the property <tt>manualValueMax</tt>
	 * 
	 * @return Maximum of value axis range, it used in XHair scaling mode
	 */
	public double getManualValueMax() {
		return ScaleModeAbstract.getManualValueMax();
	}

	/**
	 * Setter of the property <tt>manualValueMax</tt>
	 * 
	 * @param manualValueMax
	 *            Maximum of value axis range, it used in XHair scaling mode
	 */
	public void setManualValueMax(double manualValueMax) {
		ScaleModeAbstract.setManualValueMax(manualValueMax);
		forceRepaint();
	}
	
	public boolean getShowBlockHeader(){
		return isShowBlockHeader;
	}
	
	public void setShowBlockHeader(boolean isShowBlockHeader){
		this.isShowBlockHeader = isShowBlockHeader;
		if(!isShowBlockHeader){
			ChannelView.tooltipVisible = false;
		}
	}

	/**
	 * @return Returns List of currently loaded channels.
	 */
	public List<PlotDataProvider> getChannelSet() {
		List<PlotDataProvider> ret = new ArrayList<PlotDataProvider>();
		for (ChannelView cv: getChannelShowSet()) {
			for (PlotDataProvider channel: cv.getPlotDataProviders()) {
				ret.add(channel);
			}

		}
		return ret;
	}

	/**
	 * Getter of the property <tt>channelShowSet</tt>. Returns list of views for current page
	 * without influence of selection commands, like select or overlay
	 * 
	 * @return Returns the channelShowSet.
	 */
	public List<ChannelView> getChannelShowSet() {
		return channelShowSet;
	}

	/**
	 * Returns list of views for current page with influence of selection commands, like select or
	 * overlay
	 */
	public List<ChannelView> getCurrentChannelShowSet() {
		List<ChannelView> ret = new ArrayList<ChannelView>();
		Component[] comp = drawAreaPanel.getComponents();
		for (Component c: comp) {
			ret.add((ChannelView) c);
		}
		return ret;
	}

	/**
	 * @return Returns List of selected graphs Here we mean graph selected if it was selected on the
	 *         initial screen, without selected or overlayed mode.
	 */

	public List<ChannelView> getSelectedChannelShowSet() {

		return selectedChannelShowSet;

	}

	/**
	 * @return Returns List of selected graphs based on screen selection. Differ from
	 *         getSelectedChannelShowSet() while mode selected or overlay enabled.
	 */
	public List<ChannelView> getCurrentSelectedChannelShowSet() {
		List<ChannelView> ret = new ArrayList<ChannelView>();
		for (ChannelView cv: getCurrentChannelShowSet()) {
			if (cv.isSelected()) {
				ret.add(cv);
			}
		}
		Collections.sort(ret);
		return ret;

	}

	/**
	 * @return Returns List of selected channels, based on screen selection
	 */
	public List<PlotDataProvider> getCurrentSelectedChannels() {
		List<PlotDataProvider> ret = new ArrayList<PlotDataProvider>();
		for (ChannelView cv: getCurrentSelectedChannelShowSet()) {
			ret.addAll(cv.getPlotDataProviders());
		}
		return ret;

	}

	/**
	 * Setter of the property <tt>channelShowSet</tt> each channel in it's own graph or group by
	 * location in each graph
	 * 
	 * @param channels
	 *            list of traces
	 */
	public void setChannelShowSet(List<PlotDataProvider> channels) {
		synchronized (TraceView.getDataModule().getAllChannels()) {
			lg.debug("GraphPanel.setChannelShowSet begin");
			if (channels != null) {
				clearChannelShowSet();
				CommandExecutor.getInstance().clearCommandHistory(); // or do channels loading as
				// a command
				if (!TraceView.getConfiguration().getMergeLocations()) {
					for (PlotDataProvider channel: channels) {
						List<PlotDataProvider> toAdd = new ArrayList<PlotDataProvider>();
						toAdd.add(channel);
						addChannelShowSet(toAdd);
					}
				} else {
					List<PlotDataProvider> toAdd = new ArrayList<PlotDataProvider>();
					PlotDataProvider prevChannel = null;
					for (PlotDataProvider channel: channels) {
						if (prevChannel != null
								&& (!prevChannel.getNetworkName().equals(channel.getNetworkName())
										|| !prevChannel.getStation().getName().equals(channel.getStation().getName()) || !prevChannel
										.getChannelName().equals(channel.getChannelName()))) {
							ChannelView cv = channelViewFactory.getChannelView(toAdd);
							addGraph(cv);
							toAdd = new ArrayList<PlotDataProvider>();

						}
						toAdd.add(channel);
						prevChannel = channel;
					}
					if (toAdd.size() > 0) {
						addChannelShowSet(toAdd);
					}
				}
				selectedChannelShowSet = Collections.synchronizedList(new UniqueList<ChannelView>());
				if (overlay) {
					overlay = false;
					observable.setChanged();
					observable.notifyObservers("OVR OFF");
				}
				if (select) {
					select = false;
					observable.setChanged();
					observable.notifyObservers("SEL OFF");
				}
				if (rotation != null) {
					rotation = null;
					observable.setChanged();
					observable.notifyObservers("ROT OFF");
				}
				repaint();
			}
			observable.setChanged();
			observable.notifyObservers(channels);
			lg.debug("GraphPanel.setChannelShowSet end");
		}
	}

	/**
	 * Add one graph with list of channels inside it
	 */
	public void addChannelShowSet(List<PlotDataProvider> channels) {
		if (channels != null) {
			ChannelView cv = channelViewFactory.getChannelView(channels);
			addGraph(cv);
			if (this.shouldManageTimeRange) {
				if (timeRange == null) {
					setTimeRange(cv.getLoadedTimeRange());
				}
				setTimeRange(TimeInterval.getAggregate(timeRange, cv.getLoadedTimeRange()));
			}
			// repaint();
			observable.setChanged();
			observable.notifyObservers(channels);
		}
	}

	/**
	 * Clears loaded set of traces
	 */
	public void clearChannelShowSet() {
		for (ChannelView cv: channelShowSet) {
			for (PlotDataProvider channel: cv.getPlotDataProviders()) {
				channel.deleteObserver(cv);
			}
		}
		ChannelView.currentSelectionNumber = 0;
		removeAll();
		if (this.shouldManageTimeRange) {
			setTimeRange(null);
		}
	}

	/**
	 * Getter of the property <tt>unitsShowCount</tt>
	 * 
	 * @return Count of display units used to determine which subset of loaded traced should be
	 *         shown
	 */
	public int getUnitsShowCount() {
		return unitsShowCount;
	}

	/**
	 * Setter of the property <tt>unitsShowCount</tt>
	 * 
	 * @param unitsShowCount
	 *            Count of display units used to determine which subset of loaded traced should be
	 *            shown
	 */
	public void setUnitsShowCount(int unitsShowCount) {
		this.unitsShowCount = unitsShowCount;
	}

	/**
	 * @return Flag if panel should use full-size cross hairs cursor
	 */
	public boolean getShowBigCursor() {
		return showBigCursor;
	}

	/**
	 * @param showBigCursor
	 *            Flag if panel should use full-size cross hairs cursor
	 */
	public void setShowBigCursor(boolean showBigCursor) {
		this.showBigCursor = showBigCursor;
		if (showBigCursor) {
			for (ChannelView cv: channelShowSet) {
				cv.setCursor(hiddenCursor);
			}
		} else {
			for (ChannelView cv: channelShowSet) {
				cv.setCursor(crossCursor);
			}
		}
		mouseRepaint = false;
		repaint();
	}

	/**
	 * @param status
	 *            if panel should use wait cursor, used during long operations
	 */
	public void setWaitCursor(boolean status) {
		synchronized (channelShowSet) {
			if (status) {
				Cursor waitCursor = new Cursor(Cursor.WAIT_CURSOR);
				for (ChannelView cv: channelShowSet) {
					cv.setCursor(waitCursor);
				}
			} else {
				if (showBigCursor) {
					for (ChannelView cv: channelShowSet) {
						cv.setCursor(hiddenCursor);
					}
				} else {
					for (ChannelView cv: channelShowSet) {
						cv.setCursor(crossCursor);
					}
				}
			}
		}
	}

	/**
	 * Getter of the property <tt>scaleMode</tt>
	 * 
	 * @return current scaling mode
	 */
	public IScaleModeState getScaleMode() {
		return scaleMode;
	}

	/**
	 * Setter of the property <tt>scaleMode</tt>
	 * 
	 * @param scaleMode
	 *            scaling mode which panel should use
	 */
	public void setScaleMode(IScaleModeState scaleMode) {
		this.scaleMode = scaleMode;
		// returns XHair mode to all data after scale mode switching
		// manualValueMax = Integer.MIN_VALUE;
		// manualValueMin = Integer.MAX_VALUE;
		observable.setChanged();
		observable.notifyObservers(scaleMode);
		repaint();
	}

	/**
	 * Getter of the property <tt>colorMode</tt>
	 * 
	 * @return current color mode
	 */
	public IColorModeState getColorMode() {
		return colorMode;
	}

	/**
	 * Setter of the property <tt>colorMode</tt>
	 * 
	 * @param colorMode
	 *            color mode which panel should use
	 */
	public void setColorMode(IColorModeState colorMode) {
		this.colorMode = colorMode;
		observable.setChanged();
		observable.notifyObservers(colorMode);
		repaint();
	}

	/**
	 * Getter of the property <tt>meanState</tt>
	 * 
	 * @return current meaning mode
	 */
	public IMeanState getMeanState() {
		return meanState;
	}

	/**
	 * Setter of the property <tt>meanState</tt>
	 * 
	 * @param meanState
	 *            meaning mode which panel should use
	 */
	public void setMeanState(IMeanState meanState) {
		this.meanState = meanState;
		// returns XHair mode to all data after scale mode switching
		// manualValueMax = Integer.MIN_VALUE;
		// manualValueMin = Integer.MAX_VALUE;
		observable.setChanged();
		observable.notifyObservers(meanState);
		repaint();
	}

	/**
	 * Getter of the property <tt>offsetState</tt>
	 * 
	 * @return current offset mode.
	 */
	public IOffsetState getOffsetState() {
		return offsetState;
	}

	/**
	 * Setter of the property <tt>offsetState</tt>
	 * 
	 * @param offsetState
	 *            offset mode which panel should use
	 */
	public void setOffsetState(IOffsetState offsetState) {
		this.offsetState = offsetState;
		observable.setChanged();
		observable.notifyObservers(offsetState);
		repaint();
	}

	/**
	 * Getter of the property <tt>phaseState</tt>
	 * 
	 * @return current phase mode.
	 */
	public boolean getPhaseState() {
		return phaseState;
	}

	/**
	 * Setter of the property <tt>phaseState</tt>
	 * 
	 * @param phaseState
	 *            phase mode which panel should use
	 */
	public void setPhaseState(boolean phaseState) {
		this.phaseState = phaseState;
		repaint();
	}

	/**
	 * Getter of the property <tt>pickState</tt>
	 * 
	 * @return current pick mode.
	 */
	public boolean getPickState() {
		return pickState;
	}

	/**
	 * Setter of the property <tt>pickState</tt>
	 * 
	 * @param pickState
	 *            pick mode which panel should use
	 */
	public void setPickState(boolean pickState) {
		this.pickState = pickState;
		String message = null;
		if (pickState) {
			message = "PICK ON";
		} else {
			message = "PICK OFF";
		}
		observable.setChanged();
		observable.notifyObservers(message);
		repaint();
	}

	/**
	 * Sets filter. Null means filter doesn't affected. Shown traces will be redrawn with filtering.
	 * 
	 * @param filter
	 *            IFilter to set
	 */
	public void setFilter(IFilter filter) {
		lg.debug("GraphPanel: setFilter " + filter);
		if(filter != null){
			if(getMaxDataLength()>filter.getMaxDataLength()){
				if(JOptionPane.showConfirmDialog(TraceView.getFrame(), "Too long data, processing could take time. Do you want to continue?", "Warning", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
					this.filter = filter;
					observable.setChanged();
					observable.notifyObservers(filter);
					forceRepaint();
				} 
			} else {
				this.filter = filter;
				observable.setChanged();
				observable.notifyObservers(filter);
				forceRepaint();
			}
		} else {
			this.filter = filter;
			observable.setChanged();
			observable.notifyObservers(filter);
			forceRepaint();
		}
	}

	/**
	 * @return current filter, null if filter is not present
	 */
	public IFilter getFilter() {
		return filter;
	}

	/**
	 * Sets rotation. Null means rotation doesn't affected. Selected traces will be redrawn with
	 * rotation with using of "selection" mode.
	 * 
	 * @param rotation
	 *            rotation to set to set
	 */
	public void setRotation(Rotation rotation) {
		if (rotation == null) {
			drawAreaPanel.removeAll();
			for (ChannelView cv: channelShowSet) {
				drawAreaPanel.add(cv);
			}
			select = false;
			overlay = false;
			this.rotation = rotation;
			observable.setChanged();
			observable.notifyObservers("ROT OFF");
			observable.setChanged();
			observable.notifyObservers("SEL OFF");
			repaint();

		} else {
			if(rotation.getMatrix()==null){
				forceRepaint();
			} else {
				List<ChannelView> selected = getCurrentSelectedChannelShowSet();
				if (selected.size() > 0) {
					boolean dataFound = true;
					for (ChannelView cv: selected) {
						for (PlotDataProvider ch: cv.getPlotDataProviders()) {
							if (!Rotation.isComplementaryChannelExist(ch, getTimeRange())) {
							dataFound = false;
								break;
							}
						}
						if (!dataFound)
							break;
					}
					if (dataFound) {
						select();
						this.rotation = rotation;
						observable.setChanged();
						observable.notifyObservers("ROT");
						forceRepaint();
					}
				} else {
					JOptionPane.showMessageDialog(TraceView.getFrame(), "Please click check-boxes on panels to set channels to rotate",
						"Selection missing", JOptionPane.WARNING_MESSAGE);
					forceRepaint();
				}
			}
		}
	}

	/**
	 * @return current rotation, null if rotation is not present
	 */
	public Rotation getRotation() {
		return rotation;
	}
	
	/**
	 * 
	 * @return Maximum length of visible data among all visible channels
	 */
	public int getMaxDataLength(){
		int maxDataLength = 0;
		for(PlotDataProvider channel: getChannelSet()){
			int dataLength = channel.getDataLength(getTimeRange());
			if(dataLength>maxDataLength){
				maxDataLength = dataLength;
			}
		}
		return maxDataLength;
	}

	/**
	 * @return Image currently used to render position marker
	 */
	public Image getMarkPositionImage() {
		return markPositionImage;
	}

	/**
	 * @param image
	 *            image to render position marker
	 */
	public void setMarkPositionImage(Image image) {
		markPositionImage = image;
	}

	/**
	 * This method initializes drawAreaPanel
	 * 
	 * @return javax.swing.JPanel
	 */
	private DrawAreaPanel getDrawAreaPanel() {
		if (drawAreaPanel == null) {
			drawAreaPanel = new DrawAreaPanel();
		}
		return drawAreaPanel;
	}

	/**
	 * This method initializes southPanel
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getSouthPanel(boolean showTimePanel) {
		if (southPanel == null) {
			southPanel = new SouthPanel(showTimePanel);
			southPanel.setBackground(this.getBackground());
		}
		return southPanel;
	}

	/**
	 * Adds ChannelView to this panel
	 * 
	 * @param comp
	 *            ChannelView to add
	 * @return added Component
	 */
	public Component addGraph(ChannelView comp) {
		comp.setGraphPanel(this);
		channelShowSet.add(comp);
		if (showBigCursor) {
			comp.setCursor(hiddenCursor);
		} else {
			comp.setCursor(crossCursor);
		}
		Component ret = drawAreaPanel.add(comp);
		ret.doLayout();
		return ret;
	}

	/**
	 * @return current overlay mode. If overlay mode turned on, all traces loaded in graph panel
	 *         will be shown in one shared ChannelView
	 */
	public boolean getOverlayState() {
		return overlay;
	}

	/**
	 * Switch overlay mode on/off. If overlay mode turned on, all traces loaded in graph panel will
	 * be shown in one shared ChannelView
	 */
	public void overlay() {
		if (overlay) {
			drawAreaPanel.removeAll();
			if (select) {
				for (ChannelView cv: getSelectedChannelShowSet()) {
					drawAreaPanel.add(cv);
				}
			} else {
				for (ChannelView cv: channelShowSet) {
					drawAreaPanel.add(cv);
				}
			}
			overlay = false;
			ChannelView.currentSelectionNumber = 0;
		} else {
			List<ChannelView> selected = getCurrentSelectedChannelShowSet();
			if (selected.size() > 0) {
				overlay = true;
				drawAreaPanel.removeAll();
				List<PlotDataProvider> toProcess = new ArrayList<PlotDataProvider>();
				for (ChannelView cv: selected) {
					toProcess.addAll(cv.getPlotDataProviders());
				}
				ChannelView overlay = channelViewFactory.getChannelView(toProcess);
				overlay.setGraphPanel(this);
				drawAreaPanel.add(overlay);
			} else {
				JOptionPane.showMessageDialog(TraceView.getFrame(), "Please click check-boxes for panels to overlay", "Selection missing",
						JOptionPane.WARNING_MESSAGE);
			}
		}
		observable.setChanged();
		observable.notifyObservers(overlay ? "OVR ON" : "OVR OFF");
		repaint();
	}

	/**
	 * @return current selection mode. If selection mode turned on, will be shown only ChannelViews
	 *         with selected selection checkboxes.
	 */
	public boolean getSelectState() {
		return select;
	}

	/**
	 * Switch selection mode on/off. If selection mode turned on, will be shown only ChannelViews
	 * with selected selection checkboxes.
	 */
	public void select() {
		if (select) {
			drawAreaPanel.removeAll();
			for (ChannelView cv: channelShowSet) {
				drawAreaPanel.add(cv);
			}
			select = false;
			overlay = false;
			rotation = null;
			ChannelView.currentSelectionNumber = 0;
		} else {
			List<ChannelView> selected = getCurrentSelectedChannelShowSet();
			if (selected.size() > 0) {
				select = true;
				drawAreaPanel.removeAll();
				for (ChannelView cv: selected) {
					List<PlotDataProvider> toProcess = new ArrayList<PlotDataProvider>();
					for (PlotDataProvider channel: cv.getPlotDataProviders()) {
						ChannelView sel_cv = channelViewFactory.getChannelView(channel);
						sel_cv.setGraphPanel(this);
						drawAreaPanel.add(sel_cv);
					}
				}
			} else {
				JOptionPane.showMessageDialog(TraceView.getFrame(), "Please click check-boxes for panels to select", "Selection missing",
						JOptionPane.WARNING_MESSAGE);
			}
		}
		observable.setChanged();
		observable.notifyObservers(select ? "SEL ON" : "SEL OFF");
		observable.setChanged();
		observable.notifyObservers(overlay ? "OVR ON" : "OVR OFF");
		repaint();
	}

	public void remove(int index) {
		drawAreaPanel.remove(index);
		channelShowSet.remove(index);
	}

	public void remove(Component comp) {
		drawAreaPanel.remove(comp);
		channelShowSet.remove(comp);
	}

	public void removeAll() {
		drawAreaPanel.removeAll();
		channelShowSet.clear();
	}

	/**
	 * @return font to draw axis
	 */
	public static Font getAxisFont() {
		return axisFont;
	}

	/**
	 * @return earthquakes to draw on the graphs
	 */
	public Set<IEvent> getSelectedEarthquakes() {
		return selectedEarthquakes;
	}

	/**
	 * @return set of phases names to draw on the graphs
	 */
	public Set<String> getSelectedPhases() {
		return selectedPhases;
	}

	/**
	 * Sets earthquakes and phase names to draw on the graphs. Will be drawn only phases which
	 * satisfy both sets.
	 * 
	 * @param earthquakes
	 *            set of earthquakes
	 * @param phases
	 *            set of phase names
	 */
	public void setSelectedPhases(Set<IEvent> earthquakes, Set<String> phases) {
		// lg.debug("GraphPanel: setting selected values");
		selectedEarthquakes = earthquakes;
		selectedPhases = phases;
		repaint();
	}

	public void addObserver(Observer o) {
		observable.addObserver(o);
	}

	public void deleteObserver(Observer o) {
		observable.deleteObserver(o);
	}

	public void paint(Graphics g) {
		if(!paintNow){
			
		paintNow = true;
		int infoPanelWidth = channelViewFactory.getInfoAreaWidth();
		//lg.debug("Repainting graph panel");
		if (!mouseRepaint || forceRepaint || ChannelView.tooltipVisible) {
			//lg.debug("GraphPanel: force repaint");
			//RepaintManager rm = RepaintManager.currentManager(this);
			//rm.markCompletelyDirty(this);
			for (Component component: drawAreaPanel.getComponents()) {
				ChannelView view = (ChannelView) component;
				
				if (view.getHeight() == 0 || view.getWidth() == 0) {
					// Ugly hack to avoid lack of screen redraw sometimes
					//lg.debug("DrawAreaPanel: rebuilding corrupted layout");
					drawAreaPanel.doLayout();
					for (Component comp: drawAreaPanel.getComponents()) {
						comp.doLayout();
					}
					// end of ugly hack
				}
				
				view.updateData();
			}
			super.paint(g);
			g.setXORMode(new Color(204, 204, 51));
			if (mouseX > infoPanelWidth && mouseY < getHeight() - southPanel.getHeight() && showBigCursor) {
				// Drawing cursor
				// g.setXORMode(selectionColor); Hack for java 6
				//lg.debug("Force drawing cursor: " + mouseX + ", " + mouseY + ", color " + selectionColor);
				g.drawLine(infoPanelWidth, mouseY, getWidth(), mouseY);
				g.drawLine(mouseX, 0, mouseX, getHeight());
				previousMouseX = mouseX;
				previousMouseY = mouseY;
			}
			// Drawing selection area
			paintSelection(g, selectedAreaXbegin, selectedAreaXend, selectedAreaYbegin, selectedAreaYend, "Drawing");
			previousSelectedAreaXbegin = selectedAreaXbegin;
			previousSelectedAreaXend = selectedAreaXend;
			previousSelectedAreaYbegin = selectedAreaYbegin;
			previousSelectedAreaYend = selectedAreaYend;
			forceRepaint = false;
		} else {
			g.setXORMode(selectionColor);
			//lg.debug("Repainting cursor, color " + selectionColor);
			if (previousMouseX >= 0 && previousMouseY >= 0) {
				// Erasing cursor
				if (showBigCursor) {
					//lg.debug("Erasing cursor: " + previousMouseX + ", " + previousMouseY);
					g.drawLine(infoPanelWidth, previousMouseY, getWidth(), previousMouseY);
					g.drawLine(previousMouseX, 0, previousMouseX, getHeight());
				}
				previousMouseX = -1;
				previousMouseY = -1;
			}
			//lg.debug("Erasing selection area");
			paintSelection(g, previousSelectedAreaXbegin, previousSelectedAreaXend, previousSelectedAreaYbegin, previousSelectedAreaYend, "Erasing");
			previousSelectedAreaXbegin = Long.MAX_VALUE;
			previousSelectedAreaXend = Long.MIN_VALUE;
			previousSelectedAreaYbegin = Double.NaN;
			previousSelectedAreaYend = Double.NaN;
			if (mouseX > infoPanelWidth && mouseY < getHeight() - southPanel.getHeight()) {
				// Drawing cursor
				if (showBigCursor) {
					//lg.debug("Drawing cursor: " + mouseX + ", " + mouseY);
					g.drawLine(infoPanelWidth, mouseY, getWidth(), mouseY);
					g.drawLine(mouseX, 0, mouseX, getHeight());
				}
				previousMouseX = mouseX;
				previousMouseY = mouseY;
			}
			//lg.debug("Drawing selection area");
			paintSelection(g, selectedAreaXbegin, selectedAreaXend, selectedAreaYbegin, selectedAreaYend, "Drawing");
			previousSelectedAreaXbegin = selectedAreaXbegin;
			previousSelectedAreaXend = selectedAreaXend;
			previousSelectedAreaYbegin = selectedAreaYbegin;
			previousSelectedAreaYend = selectedAreaYend;
			mouseRepaint = false;
		}
		//lg.debug("End of repainting graph panel");
		paintNow = false;
		}
	}

	private void paintSelection(Graphics g, long Xbegin, long Xend, double Ybegin, double Yend, String message) {
		int infoPanelWidth = channelViewFactory.getInfoAreaWidth();
		if (Xbegin != Long.MAX_VALUE && Xend != Long.MIN_VALUE && mouseSelectionEnabled) {
			// lg.debug(message + " selection X: " + getXposition(Xbegin) + ", " +
			// getXposition(Xend));
			if (Xend > Xbegin) {
				int begPos = getXposition(Xbegin);
				int leftPos = begPos >= 0 ? begPos + infoPanelWidth + getInsets().left : infoPanelWidth + getInsets().left;
				int rightPos = begPos >= 0 ? getXposition(Xend) - getXposition(Xbegin) : getXposition(Xend) - getXposition(Xbegin) + begPos;
				g.fillRect(leftPos, 0, rightPos, getHeight());
			} else {
				int begPos = getXposition(Xend);
				int leftPos = begPos >= 0 ? begPos + infoPanelWidth + getInsets().left : infoPanelWidth + getInsets().left;
				int rightPos = begPos >= 0 ? getXposition(Xbegin) - getXposition(Xend) : getXposition(Xbegin) - getXposition(Xend) + begPos;
				g.fillRect(leftPos, 0, rightPos, getHeight());
			}
		}
		if (!new Double(Ybegin).isNaN() && !new Double(Yend).isNaN()) {
			// lg.debug(message + " selection Y: " + getScaleMode().getY(Ybegin) + ", " +
			// getScaleMode().getY(Yend));
			if (Yend > Ybegin) {
				g
						.fillRect(infoPanelWidth, getScaleMode().getY(Yend), getWidth(), getScaleMode().getY(Ybegin)
								- getScaleMode().getY(Yend));
			} else {
				g.fillRect(infoPanelWidth, getScaleMode().getY(Ybegin), getWidth(), getScaleMode().getY(Yend)
						- getScaleMode().getY(Ybegin));
			}
		}
	}

	/**
	 * @return time of last click (in internal Java format)
	 */
	public long getLastClickedTime() {
		if (mouseClickX == -1)
			return Long.MAX_VALUE;
		else {
			return getTime(mouseClickX - channelViewFactory.getInfoAreaWidth() - getInsets().left);
		}
	}

	/**
	 * @return time of first selection point while dragging (in internal Java format)
	 */
	public long getSelectionTime() {
		if (mousePressX == -1)
			return Long.MAX_VALUE;
		else {
			return getTime(mousePressX - channelViewFactory.getInfoAreaWidth() - getInsets().left);
		}
	}

	/**
	 * Computes trace time value
	 * 
	 * @param x
	 *            screen panel coordinate
	 * @return time value in internal Java format
	 */
	public long getTime(int x) {
		// lg.debug("GraphPanel getTime: " + x);
		Insets i = getInsets();
		double sr = new Double(getTimeRange().getDuration()) / (getWidth() - i.left - i.right - channelViewFactory.getInfoAreaWidth());
		return new Double(getTimeRange().getStart() + x * sr).longValue();
	}

	/**
	 * Computes screen panel coordinate
	 * 
	 * @param date
	 *            trace time value in internal Java format
	 * @return screen panel coordinate
	 */
	public int getXposition(long date) {
		if (getTimeRange() == null)
			return Integer.MAX_VALUE;
		else
			return new Double((getWidth() - channelViewFactory.getInfoAreaWidth() - getInsets().left - getInsets().right)
					* new Double((date - getTimeRange().getStart())) / new Double(getTimeRange().getDuration())).intValue();
	}

	// Methods from MouseInputListener interface to handle mouse events.

	public void mouseMoved(MouseEvent e) {
		if ((button != MouseEvent.NOBUTTON) && (e.isControlDown() || e.isShiftDown())) {
			mouseDragged(e);
		} else {
			// lg.debug("GraphPanel.mouseMoved");
			mouseX = e.getX();
			mouseY = e.getY();
			mouseRepaint = true;
			repaint();
		}
	}

	public void mouseDragged(MouseEvent e) {
		// lg.debug("GraphPanel.mouseDragged");
		mouseX = e.getX();
		mouseY = e.getY();
		mouseRepaint = true;
		repaint();

	}

	public void mouseClicked(MouseEvent e) {
		// lg.debug("GraphPanel.mouseClicked " + e.getX() + ":" + e.getY());
		if (e.getButton() == MouseEvent.BUTTON3) {
			if (mouseAdapter != null) {
				mouseAdapter.mouseClickedButton3(e.getX(), e.getY(), this);
			}
		} else if (e.getButton() == MouseEvent.BUTTON2 || ((e.getButton() == MouseEvent.BUTTON1) && (e.isShiftDown() == true))) {
			if (mouseAdapter != null) {
				mouseAdapter.mouseClickedButton2(e.getX(), e.getY(), this);
			}
		} else if (e.getButton() == MouseEvent.BUTTON1) {
			mouseClickX = e.getX();
			if (mouseAdapter != null) {
				mouseAdapter.mouseClickedButton1(e.getX(), e.getY(), this);
			}
		}
	}

	public void mouseEntered(MouseEvent e) {
		forceRepaint();
		// lg.debug("mouse entered");
	}

	public void mouseExited(MouseEvent e) {
		// lg.debug("GraphPanel.mouse exited");
		if (mouseX != -1 || mouseY != -1) {
			mouseX = -1;
			mouseY = -1;
			mouseRepaint = true;
			repaint();
		}
	}

	public void mousePressed(MouseEvent e) {
		// lg.debug("GraphPanel.mousePressed");
		mousePressX = e.getX();
		mousePressY = e.getY();
		// one-button mouse Mac OSX behaviour emulation
		if (e.getButton() == MouseEvent.BUTTON1) {
			if (e.isShiftDown()) {
				button = MouseEvent.BUTTON2;
			} else if (e.isControlDown()) {
				button = MouseEvent.BUTTON3;
			} else {
				button = MouseEvent.BUTTON1;
			}
		} else {
			button = e.getButton();
		}
	}

	public void mouseReleased(MouseEvent e) {
		// lg.debug("GraphPanel.mouseReleased");
		if (mouseSelectionEnabled) {
			button = MouseEvent.NOBUTTON;
			repaint();
		}
	}

	// From Printable interface
	public int print(Graphics pg, PageFormat pf, int pageNum) {
		if (pageNum > 0) {
			return Printable.NO_SUCH_PAGE;
		}
		Graphics2D g2 = (Graphics2D) pg;
		g2.translate(pf.getImageableX(), pf.getImageableY());
		g2.scale(g2.getClipBounds().width / new Double(this.getWidth()), g2.getClipBounds().height / new Double(this.getHeight()));
		this.paint(g2);
		return Printable.PAGE_EXISTS;
	}

	/**
	 * @param time
	 *            to start nearest segment searching
	 * @return Time of nearest segment's begin(among all loaded traces) after given time
	 */
	public Date getNearestSegmentBegin(Date time) {
		long nearestSegment = Long.MAX_VALUE;
		List<PlotDataProvider> channels = getChannelSet();
		for (PlotDataProvider channel: channels) {
			for (Segment segment: channel.getRawData()) {
				long segmentStart = segment.getStartTime().getTime();
				if (segmentStart > time.getTime() && segmentStart < nearestSegment) {
					nearestSegment = segmentStart;
				}
			}
		}
		if (nearestSegment == Long.MAX_VALUE) {
			return null;
		} else {
			return new Date(nearestSegment);
		}
	}

	/**
	 * @param time
	 *            to start nearest segment searching
	 * @return Time of nearest segment's end(among all loaded traces) before given time
	 */
	public Date getNearestSegmentEnd(Date time) {
		long nearestSegment = Long.MIN_VALUE;
		List<PlotDataProvider> channels = getChannelSet();
		for (PlotDataProvider channel: channels) {
			for (Segment segment: channel.getRawData()) {
				long segmentEnd = segment.getEndTime().getTime();
				if (segmentEnd < time.getTime() && segmentEnd > nearestSegment) {
					nearestSegment = segmentEnd;
				}
			}
		}
		if (nearestSegment == Long.MIN_VALUE) {
			return null;
		} else {
			return new Date(nearestSegment);
		}
	}
	
	public void setBackground(Color color){
		super.setBackground(color);
		if(southPanel != null){
			southPanel.setBackground(color);
		}
	}

	/**
	 * Time-axis panel used by GraphPanel
	 */
	class AxisPanel extends JPanel {
		private DateAxis axis = null;

		public AxisPanel() {
			super();
			setMinimumSize(new Dimension(200, 20));
			setPreferredSize(new Dimension(200, 20));
			axis = new DateAxis();
			axis.setTimeZone(TimeZone.getTimeZone("GMT"));
			axis.setDateFormatOverride(TimeInterval.df_long);
		}

		/**
		 * @param df
		 *            date format to use in axis
		 * @see TimeInterval
		 */
		private void setDateFormat(SimpleDateFormat df) {
			if (!axis.getDateFormatOverride().equals(df)) {
				axis.setDateFormatOverride(df);
			}
		}

		/**
		 * @param ti
		 *            time interval of axis
		 */
		public void setTimeRange(TimeInterval ti) {
			lg.debug("AxisPanel setTimeRange: " + ti);
			boolean needwait = false;
			if (axis.getMinimumDate().getTime() == 0 && axis.getMaximumDate().getTime() == 1) {
				needwait = true;
			}
			if (ti != null) {
				axis.setMinimumDate(ti.getStartTime());
				axis.setMaximumDate(ti.getEndTime());
				if (ti.getDuration() < 6000) {
					axis.setDateFormatOverride(TimeInterval.df);
				} else if (ti.getDuration() < 300000) {
					axis.setDateFormatOverride(TimeInterval.df_middle);
				} else {
					axis.setDateFormatOverride(TimeInterval.df_long);
				}
			} else {
				axis.setMinimumDate(new Date(0));
				axis.setMaximumDate(new Date(1000000));
				axis.setDateFormatOverride(TimeInterval.df_long);
			}
			if (needwait) {
				// to let finish previous repaint() and avoid blank axis
				try {
					Thread.sleep(60);
				} catch (InterruptedException e) {
					// do nothing
				}
			}
			repaint();
		}

		public void paintComponent(Graphics g) {
			//lg.debug("AxisPanel paintComponent");
			super.paintComponent(g);
			int infoPanelWidth = channelViewFactory.getInfoAreaWidth();
			if (axis.getMinimumDate().getTime() != 0 && axis.getMaximumDate().getTime() != 1) {
				//lg.debug("min date " + axis.getMinimumDate() + ", max date " + axis.getMaximumDate());
				axis.draw((Graphics2D) g, 0, new Rectangle(infoPanelWidth + getInsets().left, 0, getWidth(), getHeight()), new Rectangle(
						infoPanelWidth + getInsets().left, 0, getWidth(), 10), RectangleEdge.BOTTOM, null);
			}
		}
	}

	/**
	 * Bottom panel with general timing information - start time, shown duration, end time, used in
	 * GraphPanel
	 */
	class TimeInfoPanel extends JPanel {
		private JLabel start = null;
		private JLabel duration = null;
		private JLabel end = null;
		GridLayout gridLayout = null;

		public TimeInfoPanel() {
			super();
			setFont(getAxisFont());
			gridLayout = new GridLayout();
			start = new JLabel("", SwingConstants.LEFT);
			duration = new JLabel("", SwingConstants.CENTER);
			end = new JLabel("", SwingConstants.RIGHT);
			setLayout(gridLayout);
			gridLayout.setColumns(3);
			gridLayout.setRows(1);
			add(start);
			add(duration);
			add(end);
		}

		public void update(TimeInterval ti) {
			lg.debug("InfoPanel updating, ti = " + ti);
			if (ti != null) {
				start.setText(TimeInterval.formatDate(ti.getStartTime(), TimeInterval.DateFormatType.DATE_FORMAT_NORMAL));
				duration.setText(ti.convert());
				end.setText(TimeInterval.formatDate(ti.getEndTime(), TimeInterval.DateFormatType.DATE_FORMAT_NORMAL));
			}
		}
	}

	class SouthPanel extends JPanel {
		private AxisPanel axisPanel;
		private TimeInfoPanel infoPanel;

		public SouthPanel(boolean showTimePanel) {
			super();
			GridLayout gridLayout = new GridLayout();
			axisPanel = new AxisPanel();
			axisPanel.setBackground(this.getBackground());
			infoPanel = new TimeInfoPanel();
			infoPanel.setBackground(this.getBackground());
			setLayout(gridLayout);
			gridLayout.setColumns(1);
			gridLayout.setRows(0);
			add(axisPanel);
			if(showTimePanel){
				add(infoPanel);
			}
		}

		public AxisPanel getAxisPanel() {
			return axisPanel;
		}

		public TimeInfoPanel getInfoPanel() {
			return infoPanel;
		}
		
		public void setBackground(Color color){
			super.setBackground(color);
			if(axisPanel!=null){
			axisPanel.setBackground(color);
			}
			if(infoPanel!=null){
			infoPanel.setBackground(color);
			}
		}
	}

	class DrawAreaPanel extends JPanel {
		public DrawAreaPanel() {
			super();
			GridLayout gridLayout = new GridLayout();
			gridLayout.setColumns(1);
			gridLayout.setRows(0);
			setLayout(gridLayout);
		}

		public void paint(Graphics g) {
			lg.debug("DrawAreaPanel paint() Height: " + getHeight() + ", width: " + getWidth() + ", " + getComponents().length + " ChannelViews");
			super.paint(g);
		}
	}

	public void update(Observable observable, Object obj) {
		lg.debug(this + ": update request from " + observable);
		if (obj instanceof IScaleModeState) {
			setScaleMode((IScaleModeState) obj);
		} else if (obj instanceof IColorModeState) {
			setColorMode((IColorModeState) obj);
		} else if (obj instanceof IMeanState) {
			setMeanState((IMeanState) obj);
		} else if (obj instanceof IOffsetState) {
			setOffsetState((IOffsetState) obj);
		}
	}

	public class GraphPanelObservable extends Observable {
		public void setChanged() {
			super.setChanged();
		}
	}
}
