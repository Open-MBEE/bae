package gov.nasa.jpl.ae.event;

import gov.nasa.jpl.ae.solver.Constraint;
import gov.nasa.jpl.ae.solver.ObjectDomain;
import gov.nasa.jpl.ae.solver.Solver;
import gov.nasa.jpl.ae.solver.Variable;
import gov.nasa.jpl.mbee.util.Pair;
import gov.nasa.jpl.mbee.util.Random;
import gov.nasa.jpl.mbee.util.Utils;

import java.util.*;

/**
 * This solver updates variables in order based on dependencies derived from
 * equals constraints.
 *
 * Assume that the are n variables, X = x1, x2, ..., xn, and m constraints,
 * C = c1, c2, ..., cn.  And, each constraint, cj in C, has the form
 * xi = f(X') for xi in X and X' subseteq X.
 *
 * A graph is constructed where each
 * Assuming that all constraints are violated,
 * A graph is kept for de
 *
 */
public class DependencyGraphSolver implements Solver {
    Collection<Constraint> constraints = null;

    LinkedHashMap<Variable, Set<Constraint>> dependenciesForTarget = new LinkedHashMap<>();
    LinkedHashMap<Constraint, Variable> targetsOfDependencies = new LinkedHashMap<>();
    LinkedHashSet<Variable> staleVariables = new LinkedHashSet<>();
    LinkedHashSet<Constraint> constraintsLikeDependencies = new LinkedHashSet<>();
    LinkedHashMap<Constraint, Set<Variable>> variablesInConstraintRHS = new LinkedHashMap<>();
    LinkedHashMap<Variable, Set<Constraint>> dependenciesWithVarInRHS = new LinkedHashMap<>();
    LinkedHashSet<Variable> changedVariables = new LinkedHashSet<>();

    int defaultMaxLoops = 10;

    //LinkedHashMap<Variable, Set<Variable> > variableDependencies = LinkedHashMap<>();
    //LinkedHashMap<Constraint, Set<Constraint> > constraintGraph;

    public DependencyGraphSolver(Collection<Constraint> constraints) {
        this.constraints = constraints;
        init();
    }

    public boolean solve() {
        if ( constraints != null ) {
            return solve(constraints);
        }
        return false;
    }

    @Override public boolean solve( Collection<Constraint> constraints ) {
        return solve( constraints, defaultMaxLoops );
    }
    public boolean solve( Collection<Constraint> constraints, int maxLoops ) {
        this.constraints = constraints;
        init();
        LinkedHashSet<Constraint> skipped = new LinkedHashSet<>();
        solveDependencies( maxLoops, skipped );
        for ( Constraint c : skipped ) {
            if ( !c.isSatisfied( false, null ) ) {
                c.satisfy( false, null );
            }
        }
        Collection<Constraint> unsat = getUnsatisfiedConstraints();
        return Utils.isNullOrEmpty( unsat );
    }

    public boolean solveDependencies( int maxLoops ) {
        LinkedHashSet<Constraint> skipped = new LinkedHashSet<>();
        return solveDependencies( maxLoops, skipped );
    }
    public boolean solveDependencies( int maxLoops, LinkedHashSet<Constraint> skipped ) {
        boolean changed = true;
        int numLoops = 0;
        LinkedHashSet<Variable> changedVars = new LinkedHashSet<>();
        while (changed && numLoops < maxLoops) {
            changed = false;
            changedVars.addAll( changedVariables );
            changedVariables.clear();
            for ( Constraint c : constraints ) {
                if ( !constraintsLikeDependencies.contains( c ) ) {
                    skipped.add( c );
                    continue;
                }
                boolean didChange = satisfyDependency( c, null );
                if (didChange) changed = true;
            }
            numLoops += 1;
            changed = !changedVariables.isEmpty();
        }
        changedVars.addAll( changedVariables );
        changedVariables = changedVars;
        return changed;
    }

    public boolean solveDependencies() {
        LinkedHashSet<Constraint> skipped = new LinkedHashSet<>();
        return solveDependencies( skipped );
    }
    public boolean solveDependencies( LinkedHashSet<Constraint> skipped ) {
        int numChangedVars = changedVariables.size();
        for ( Constraint c : constraints ) {
            if ( !constraintsLikeDependencies.contains( c ) ) {
                skipped.add( c );
                continue;
            }
            satisfyDependency( c, null );
        }
        int newNumChangedVars = changedVariables.size();
        if ( newNumChangedVars > newNumChangedVars ) {
            return true;
        }
        return false;
    }

    public boolean satisfyDependency( Constraint c, Set<Object> seen ) {
        Pair< Boolean, Set< Object > > pair = Utils.seen( c, true, seen );
        if ( pair.first ) return true;
        seen = pair.second;

        boolean succ = true;
        if ( !c.isSatisfied( false, null ) ) {
            Set<Variable> vars = variablesInConstraintRHS.get( c );
            for ( Variable v : vars ) {
                if ( staleVariables.contains( v ) ) {
                    boolean s = satisfyVariable( v, seen );
                    if ( !s ) succ = false;
                }
            }
            Variable v = targetsOfDependencies.get( c );
            Object oldVal = v.getValue( false );
            if ( c instanceof ConstraintExpression ) {
                ConstraintExpression ce = (ConstraintExpression)c;
                boolean s = ce.applyAsIfDependency();
                if ( !s ) succ = false;
            } else {
                // should be a dependency
                boolean s = c.satisfy( false, null );
                if ( !s ) succ = false;
            }

            // Update staleVariables.
            if ( succ ) {
                staleVariables.remove( v );
                Object newVal = v.getValue( false );
                // If v's value changed, mark dependent variables stale.
                if ( !Utils.valuesEqual( oldVal, newVal ) ) {
                    changedVariables.add( v );
                    Set<Constraint> constrs = dependenciesWithVarInRHS.get( v );
                    if ( constrs != null ) {
                        for ( Constraint cc : constrs ) {
                            Variable vv = targetsOfDependencies.get( cc );
                            if ( vv != null ) {
                                staleVariables.add( vv );
                            }
                        }
                    }
                }
            }


        }
        return succ;
    }

    public boolean satisfyVariable( Variable v, Set<Object> seen ) {
        Pair< Boolean, Set< Object > > pair = Utils.seen( v, true, seen );
        if ( pair.first ) return true;
        seen = pair.second;

        // Just pick one of the dependencies for the variable and satisfy it.
        Set<Constraint> deps = dependenciesForTarget.get( v );
        if ( !Utils.isNullOrEmpty( deps ) ) {
            int i = Random.global.nextInt( deps.size() );
            Constraint c = (Constraint)deps.toArray()[ i ];
            boolean succ = satisfyDependency(c, seen);
            return succ;
        }
        return true;
    }

    protected void init() {
        if ( constraints == null ) return;
        dependenciesForTarget.clear();
        staleVariables.clear();
        constraintsLikeDependencies.clear();
        variablesInConstraintRHS.clear();
        dependenciesWithVarInRHS.clear();
        for ( Constraint c : constraints ) {
            Parameter param = null;
            Object expr = null;
            if ( c instanceof Dependency ) {
                Dependency d = (Dependency)c;
                param = d.getParameter();
                expr = d.expression;
            } else if ( c instanceof ConstraintExpression ) {
                ConstraintExpression ce = (ConstraintExpression)c;
                Pair<Parameter<?>, Object> p = ce.dependencyLikeVar();
                if ( p != null ) {
                    param = p.first;
                    expr = p.second;
                }
            }
            if ( param != null && expr != null ) {
                constraintsLikeDependencies.add( c );
                targetsOfDependencies.put( c, param );
                Utils.add( dependenciesForTarget, param, c );
//                if ( param.isStale() ||
//                     ( expr instanceof LazyUpdate && ((LazyUpdate)expr).isStale() ) ) {
                    staleVariables.add( param );
//                }
                Set<Parameter<?>> vars = HasParameters.Helper
                        .getParameters( expr, false, null, true );
                variablesInConstraintRHS.put( c, new LinkedHashSet<Variable>( vars ) );
                for ( Variable v : vars ) {
                    Utils.add(dependenciesWithVarInRHS, v, c);
                }
            }
        }
    }

    @Override public Collection<Constraint> getUnsatisfiedConstraints() {
        ArrayList<Constraint> unsat = new ArrayList<>();
        if ( constraints != null ) {
            for ( Constraint c : constraints ) {
                if ( !c.isSatisfied( false, null ) ) {
                    unsat.add( c );
                }
            }
        }
        return unsat;
    }

    @Override public Collection<Constraint> getConstraints() {
        return constraints;
    }

    @Override public void setConstraints( Collection<Constraint> constraints ) {
        this.constraints = constraints;
    }

    @Override public int getNumberOfResolvedConstraints() {
        return getUnsatisfiedConstraints().size();
    }

/*
    public static boolean test() {
        DoubleParameter x = new DoubleParameter(  );
        ConstraintExpression ce1 = new ConstraintExpression( new Functions.EQ<>(  ) );
        return true;
    }

    public static void main(String[] args) {

    }
*/

}
