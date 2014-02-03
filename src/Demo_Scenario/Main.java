package Demo_Scenario;

import gov.nasa.jpl.ae.event.Parameter;
import gov.nasa.jpl.ae.event.IntegerParameter;
import gov.nasa.jpl.ae.event.DoubleParameter;
import gov.nasa.jpl.ae.event.StringParameter;
import gov.nasa.jpl.ae.event.BooleanParameter;
import gov.nasa.jpl.ae.event.Timepoint;
import gov.nasa.jpl.ae.event.Expression;
import gov.nasa.jpl.ae.event.ConstraintExpression;
import gov.nasa.jpl.ae.event.Functions;
import gov.nasa.jpl.ae.event.FunctionCall;
import gov.nasa.jpl.ae.event.ConstructorCall;
import gov.nasa.jpl.ae.event.Call;
import gov.nasa.jpl.ae.event.Effect;
import gov.nasa.jpl.ae.event.TimeDependentConstraintExpression;
import gov.nasa.jpl.ae.event.Dependency;
import gov.nasa.jpl.ae.event.ElaborationRule;
import gov.nasa.jpl.ae.event.EventInvocation;
import gov.nasa.jpl.ae.event.ParameterListenerImpl;
import gov.nasa.jpl.ae.event.Event;
import gov.nasa.jpl.ae.event.DurativeEvent;
import gov.nasa.jpl.ae.event.TimeVarying;
import gov.nasa.jpl.ae.event.TimeVaryingMap;
import gov.nasa.jpl.ae.event.TimeVaryingPlottableMap;
import gov.nasa.jpl.mbee.util.ClassUtils;
import gov.nasa.jpl.mbee.util.Debug;
import gov.nasa.jpl.mbee.util.Utils;

import java.util.Vector;
import java.util.Map;

public class Main extends BobCreator {

    public Main() {
        super(new Expression<Integer>( 0 ));
    }

    public static void main(String[] args) {
        Timepoint.setUnits("seconds");
        Timepoint.setEpoch("Sun Aug 05 23:30:00 PDT 2012");
        Timepoint.setHorizonDuration(3600*12);
        Debug.turnOff();
        Main scenario = new Main();

        scenario.setTimeoutSeconds( 14400 );
        scenario.setUsingTimeLimit( true );
        scenario.setMaxPassesAtConstraints( 10000 );
        scenario.setUsingLoopLimit( true );
        scenario.setLoopsPerSnapshot( 5 );
        scenario.setMaxLoopsWithNoProgress( 300 );
        scenario.setBaseSnapshotFileName( "DemoScenarioTimerAt4hrs.txt" );

        double simDuration = 40.0;
        double speedup = Timepoint.getHorizonDuration() / simDuration;
        scenario.executeAndSimulate( speedup );
    }
}
