/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package sim.portrayal.inspector;
import java.awt.*;
import java.awt.event.*;
import sim.util.*;
import sim.display.*;
import sim.engine.*;
import javax.swing.*;
import sim.util.gui.*;
import sim.util.media.chart.*;
import org.jfree.data.xy.*;
import org.jfree.data.general.*;

/** An abstract superclass for property inspectors which use sim.util.ChartGenerator to produce charts.
    Contains a number of utility methods that these property inspector often have in commmon.  Each ChartingPropertyInspector
    works with a single data series on a single chart.  Additionally, each ChartingPropertyInspector has access to the
    global attributes common to the ChartGenerator.
        
    <p>To construct a ChartingPropertyInspector subclass, you need to override the methods validChartGenerator(generator),
    which indicates if the generator is one you know how to work with; createNewGenerator(), which produces a new
    chart generator when you need to create one from scratch, and updateSeries(...), which revises the series to possibly
    reflect new data.
                
    <p>Additionally, you may wish to override includeAggregationMethodAttributes() to indicate whether or not the 
    ChartingPropertyInspector should include an aggregation method in its global attributes.
    If so, the aggregation method will be stored in globalAttributes.aggregationMethod
    and will be one of AGGREGATIONMETHOD_CURRENT (don't aggregate), AGGREGATIONMETHOD_MIN (use the minimum of
    the aggregated data over time), AGGREGATIONMETHOD_MAX (use the maximum), or AGGREGATIONMETHOD_MEAN (use
    the mean).   The aggregation interval -- how much time you should wait for before dumping the aggregated
    results into the time series -- will be stored in globalAttriutes.interval. 
*/

public abstract class ChartingPropertyInspector extends PropertyInspector
    {
    /** The ChartGenerator used by this ChartingPropertyInspector */
    protected ChartGenerator generator;
    double lastTime  = Schedule.BEFORE_SIMULATION;
        
    /** Called when the inspector is being asked to use an existing ChartGenerator.  Should return true if the
        ChartGenerator is compatable with this inspector. */
    protected abstract boolean validChartGenerator(ChartGenerator generator);

    /** Called when the inspector is being asked to create a new ChartGenerator from scratch. */
    protected abstract ChartGenerator createNewGenerator();

    /** Called from update() to inform the ChartingPropertyInspector that it may want to update its data series
        to reflect new data at this time.  The value lastTime indicates the previous timestep when this method was
        called.  It's possible that time == lastTime, that is, the method is called multiple times and nothing has
        changed. */
    protected abstract void updateSeries(double time, double lastTime);

    /** Returns true if a widget should be inserted into the series attributes that allows the user to specify
        an aggregation method.  If so, the aggregation method will be stored in globalAttributes.aggregationMethod
        and will be one of AGGREGATIONMETHOD_CURRENT (don't aggregate), AGGREGATIONMETHOD_MIN (use the minimum of
        the aggregated data over time), AGGREGATIONMETHOD_MAX (use the maximum), or AGGREGATIONMETHOD_MEAN (use
        the mean).   The aggregation interval -- how much time you should wait for before dumping the aggregated
        results into the time series -- will be stored in globalAttriutes.interval. */
    protected boolean includeAggregationMethodAttributes() { return true; }
        
    /** Produces a ChartingPropertyInspector which tracks property number index from the given properties list,
        stored in the provided parent frame, and applied in the given simulation.  This constructor will give the
        user a chance to cancel the construction, in which case validInspector will be set to false, and generator
        may be set to null.  In that case, assume that the inspector will be deleted immediately after.  */
    public ChartingPropertyInspector(Properties properties, int index, Frame parent, final GUIState simulation)
        {
        super(properties,index,parent,simulation);
        generator = chartToUse( properties.getName(index), parent, simulation );
        globalAttributes = findGlobalAttributes();  // so we share timer information.  If null, we're in trouble.
        validInspector = (generator!=null);
        }

    /** Used to find the global attributes that another inspector has set so I can share it. */
    GlobalAttributes findGlobalAttributes()
        {
        if (generator == null) return null;  // got a problem
        int len = generator.getGlobalAttributeCount();
        for(int i = 0; i < len ; i ++)
            if ((generator.getGlobalAttribute(i) instanceof GlobalAttributes))
                return (GlobalAttributes) generator.getGlobalAttribute(i);
        return null;
        }
                
    /** Used to find the global attributes that another inspector has set so I can share it. */
    ChartGenerator chartToUse( final String sName, Frame parent, final GUIState simulation )
        {
        Bag charts = new Bag();
        if( simulation.guiObjects != null )
            for( int i = 0 ; i < simulation.guiObjects.numObjs ; i++ )
                if( simulation.guiObjects.objs[i] instanceof ChartGenerator &&
                    validChartGenerator((ChartGenerator)(simulation.guiObjects.objs[i])))
                    charts.add( simulation.guiObjects.objs[i] );
        if( charts.numObjs == 0 )
            return createNewChart(simulation);

        // init the dialog panel
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());

        String[] chartNames = new String[ charts.numObjs + 1 ];

        chartNames[0] = "[Create a New Chart]";
        for( int i = 0 ; i < charts.numObjs ; i++ )
            chartNames[i+1] = ((ChartGenerator)(charts.objs[i])).getTitle();

        // add widgets
        JPanel panel2 = new JPanel();
        panel2.setLayout(new BorderLayout());
        panel2.setBorder(new javax.swing.border.TitledBorder("Plot on Chart..."));
        JComboBox encoding = new JComboBox(chartNames);
        panel2.add(encoding, BorderLayout.CENTER);
        p.add(panel2, BorderLayout.SOUTH);
                
        // ask
        if(JOptionPane.showConfirmDialog(parent, p,"Create a New Chart...",
                                         JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return null;

        if( encoding.getSelectedIndex() == 0 )
            return createNewChart(simulation);
        else
            return (ChartGenerator)(charts.objs[encoding.getSelectedIndex()-1]);
        }
        
                                        
    static final int AGGREGATIONMETHOD_CURRENT = 0;
    static final int AGGREGATIONMETHOD_MAX = 1;
    static final int AGGREGATIONMETHOD_MIN = 2;
    static final int AGGREGATIONMETHOD_MEAN = 3;
        
    static final int REDRAW_ALWAYS = 0;
    static final int REDRAW_TENTH_SEC = 1;
    static final int REDRAW_HALF_SEC = 2;
    static final int REDRAW_ONE_SEC = 3;
    static final int REDRAW_TWO_SECS = 4;
    static final int REDRAW_FIVE_SECS = 5;
    static final int REDRAW_TEN_SECS = 6;
    static final int REDRAW_DONT = 7;
    protected GlobalAttributes globalAttributes;

    protected class GlobalAttributes extends JPanel
        {
        public long interval = 1;
        public int aggregationMethod = AGGREGATIONMETHOD_CURRENT;
        public int redraw = REDRAW_HALF_SEC;

        public GlobalAttributes()
            {
            setLayout(new BorderLayout());
            LabelledList list = new LabelledList(
                includeAggregationMethodAttributes() ? "Add Data..." : "Redraw");
            add(list,BorderLayout.CENTER);
                        
            if (includeAggregationMethodAttributes())
                {
                NumberTextField stepsField = new NumberTextField(1,true)
                    {
                    public double newValue(double value)
                        {
                        value = (long)value;
                        if (value <= 0) return currentValue;
                        else 
                            {
                            interval = (long)value;
                            return value;
                            }
                        }
                    };
                                                                                        
                list.addLabelled("Every",stepsField);
                list.addLabelled("",new JLabel("...Timesteps"));

                String[] optionsLabel = { "Current", "Maximum", "Minimum", "Mean" };
                final JComboBox optionsBox = new JComboBox(optionsLabel);
                optionsBox.setSelectedIndex(aggregationMethod);
                optionsBox.addActionListener(
                    new ActionListener()
                        {
                        public void actionPerformed(ActionEvent e)
                            {
                            aggregationMethod = optionsBox.getSelectedIndex();
                            }
                        });
                list.addLabelled("Using", optionsBox);
                }
                        
            String[] optionsLabel2 = new String[]{ "When Adding Data", "Every 0.1 Seconds", "Every 0.5 Seconds", 
                                                   "Every Second", "Every 2 Seconds", "Every 5 Seconds", "Every 10 Seconds", "Never" };
            final JComboBox optionsBox2 = new JComboBox(optionsLabel2);
            optionsBox2.setSelectedIndex(redraw);
            optionsBox2.addActionListener(
                new ActionListener()
                    {
                    public void actionPerformed(ActionEvent e)
                        {
                        redraw = optionsBox2.getSelectedIndex();
                        generator.update();  // keep up-to-date
                        }
                    });
            if (includeAggregationMethodAttributes())
                list.addLabelled("Redraw", optionsBox2);
            else list.add(optionsBox2);
            }
        }
                
    Thread timer = null;
    public void updateAfterTime(final long milliseconds)
        {
        if (timer == null)
            {
            timer= sim.util.Utilities.doLater(milliseconds, new Runnable()
                {
                public void run()
                    {
                    if (generator!=null)
                        {
                        generator.update();  // keep up-to-date
                        }
                    // this is in the Swing thread, so it's okay
                    timer = null;
                    }
                });
            }
        }


    ChartGenerator createNewChart( final GUIState simulation)
        {
        generator = createNewGenerator();
        globalAttributes = new GlobalAttributes();
        generator.addGlobalAttribute(globalAttributes);  // it'll be added last
                
        // set up the simulation -- need a new name other than guiObjects: and it should be
        // a HashMap rather than a Bag.
        if( simulation.guiObjects == null )
            simulation.guiObjects = new Bag();
        simulation.guiObjects.add( generator );
        final JFrame f = generator.createFrame(simulation);
        WindowListener wl = new WindowListener()
            {
            public void windowActivated(WindowEvent e) {}
            public void windowClosed(WindowEvent e) {}
            public void windowClosing(WindowEvent e) { generator.quit(); }
            public void windowDeactivated(WindowEvent e) {}
            public void windowDeiconified(WindowEvent e) {}
            public void windowIconified(WindowEvent e) {}
            public void windowOpened(WindowEvent e) {}
            };
        f.addWindowListener(wl);
        f.setVisible(true);

        return generator;
        }

    // Utility method.  Returns a filename guaranteed to end with the given ending.
    static String ensureFileEndsWith(String filename, String ending)
        {
        // do we end with the string?
        if (filename.regionMatches(false,filename.length()-ending.length(),ending,0,ending.length()))
            return filename;
        else return filename + ending;
        }

    public void updateInspector()
        {
        double time = simulation.state.schedule.time();
        if (lastTime < time)
            {                   
            updateSeries(time, lastTime);
            lastTime = time;
                        
            // now determine when to update
            switch(globalAttributes.redraw) 
                {
                case REDRAW_ALWAYS:  // do it now
                    generator.update();
                    break;
                case REDRAW_TENTH_SEC:
                    updateAfterTime(100);
                    break;
                case REDRAW_HALF_SEC:
                    updateAfterTime(500);
                    break;
                case REDRAW_ONE_SEC:
                    updateAfterTime(1000);
                    break;
                case REDRAW_TWO_SECS:
                    updateAfterTime(2000);
                    break;
                case REDRAW_FIVE_SECS:
                    updateAfterTime(5000);
                    break;
                case REDRAW_TEN_SECS:
                    updateAfterTime(10000);
                    break;
                case REDRAW_DONT:  // do nothing
                    break;
                default:
                    System.err.println("Unknown redraw time specified");
                }
            }
        }
    
    public boolean shouldCreateFrame()
        {
        return false;
        }

    }