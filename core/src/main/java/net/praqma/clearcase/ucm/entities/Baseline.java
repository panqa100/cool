package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;

import net.praqma.clearcase.Cool;
import net.praqma.clearcase.Deliver;
import net.praqma.clearcase.PVob;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.EntityNotLoadedException;
import net.praqma.clearcase.exceptions.NothingNewException;
import net.praqma.clearcase.exceptions.UCMEntityNotFoundException;
import net.praqma.clearcase.exceptions.UnableToCreateEntityException;
import net.praqma.clearcase.exceptions.UnableToGetEntityException;
import net.praqma.clearcase.exceptions.UnableToInitializeEntityException;
import net.praqma.clearcase.exceptions.UnableToListBaselinesException;
import net.praqma.clearcase.exceptions.UnableToLoadEntityException;
import net.praqma.clearcase.exceptions.UnableToPromoteBaselineException;
import net.praqma.clearcase.interfaces.Diffable;
import net.praqma.clearcase.ucm.entities.Project.PromotionLevel;
import net.praqma.clearcase.ucm.entities.Version.Status;

import net.praqma.clearcase.ucm.view.SnapshotView;
import net.praqma.logging.Config;
import net.praqma.util.debug.Logger;
import net.praqma.util.execute.AbnormalProcessTerminationException;

public class Baseline extends UCMEntity implements Diffable {

	transient static private Logger logger = Logger.getLogger();

	/* Baseline specific fields */

	private Component component = null;
	private Project.PromotionLevel plevel = Project.PromotionLevel.INITIAL;
	private Stream stream = null;
	private ArrayList<Activity> activities = null;

	private static java.util.logging.Logger tracer = java.util.logging.Logger.getLogger(Config.GLOBAL_LOGGER_NAME);

	public enum LabelBehaviour {
		NOLABEL,
		INCREMENTAL,
		FULL,
		full,
		none,
		DEFAULT;

		public String toArgument() {
			switch( this ) {
			case NOLABEL:
				return "-nlabel";
			case INCREMENTAL:
				return "-incremental";
			case FULL:
				return "-full";
			case full:
				return "-full";
			case none:
				return "-nlabel";
			default:
				return "";
			}
		}

		public static LabelBehaviour fromIncremental( boolean incremental ) {
			return ( incremental ? INCREMENTAL : FULL );
		}
	}

	Baseline() {
		super( "baseline" );
		tracer.entering(Baseline.class.getSimpleName(), "Baseline");
		tracer.exiting(Baseline.class.getSimpleName(), "Baseline");
	}

	/**
	 * Load the Baseline into memory from ClearCase.<br>
	 * This function is automatically called when needed by other functions.
	 * 
	 * @throws UnableToLoadEntityException
	 * @throws UnableToInitializeEntityException 
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Baseline load() throws UnableToLoadEntityException, UnableToInitializeEntityException {
		tracer.entering(Baseline.class.getSimpleName(), "load");
		//logger.debug( "Loading baseline " + this );

		String result = "";

		String cmd = "desc -fmt %n" + Cool.delim + "%X[component]p" + Cool.delim + "%X[bl_stream]p" + Cool.delim + "%[plevel]p" + Cool.delim + "%u" + Cool.delim + "%Nd" + Cool.delim + "%[label_status]p" + Cool.delim + "%[master]p " + this;
		try {
			result = Cleartool.run( cmd ).stdoutBuffer.toString();
		} catch( Exception e ) {
			UnableToLoadEntityException exception = new UnableToLoadEntityException( this, e );

			tracer.severe(String.format("Exception thrown type: %s; message: %s", e.getClass(), e.getMessage()));

			throw exception;
		}

		String[] rs = result.split( UCMEntity.delim );
		logger.debug("Result:" + result);
		tracer.finest("Result:" + result);

		/* Component . component:GENI_Source@\bbComponent */
		String c = ( rs[1].matches( "^component:.*$" ) ? "" : "component:" ) + ( rs[1].matches( ".*@" + PVob.rx_tag_format + "$" ) ? rs[1] : rs[1] + "@" + this.pvob );
		/* Stream */
		if( rs[2].trim().length() > 0 ) {
			String s = ( rs[2].matches( "^stream:.*$" ) ? "" : "stream:" ) + ( rs[2].matches( ".*@" + PVob.rx_tag_format + "$" ) ? rs[2] : rs[2] + "@" + this.pvob );
			this.stream = Stream.get( s );
		} else {
			tracer.finest("No stream set for baseline" );
			logger.debug( "No stream set for baseline" );
		}

		/* Now with factory creation! */
		this.component = Component.get( c );
		this.plevel = Project.getPlevelFromString( rs[3] );
		this.user = rs[4];
		try {
			logger.debug("Result[5]:" + rs[5]);
			synchronized( dateFormatter ) {
				this.date = dateFormatter.parse( rs[5] );
			}
		} catch( ParseException e ) {
			logger.debug( "Unable to parse date: " + e.getMessage() );
			this.date = null;
		}

		this.labelStatus = getLabelStatusFromString( rs[6] );

		/* mastership */
		this.mastership = rs[7];

		//logger.debug( "[BASELINE] component: " + this.component + ", stream: " + this.stream + ", plevel: " + this.plevel + ", user: " + this.user + ", date: " + this.date + ", label " + this.labelStatus );

		activities = new ArrayList<Activity>();
		this.loaded = true;

		tracer.exiting(Baseline.class.getSimpleName(), "load", this);		

		return this;
	}

	public static Baseline create( String basename, Component component, File view, LabelBehaviour labelBehaviour, boolean identical ) throws UnableToInitializeEntityException, UnableToCreateEntityException, NothingNewException {
		tracer.entering(Baseline.class.getSimpleName(), "create", new Object[]{basename, component, view, labelBehaviour, identical} );	

		Baseline baseline = create( basename, component, view, labelBehaviour, identical, null, null );

		tracer.exiting(Baseline.class.getSimpleName(), "create", baseline);

		return baseline;
	}

	/**
	 * Given a baseline basename, a component and a view, the baseline is
	 * created.
	 */
	public static Baseline create( String basename, Component component, File view, LabelBehaviour labelBehaviour, boolean identical, List<Activity> activities, List<Component> depends ) throws UnableToInitializeEntityException, UnableToCreateEntityException, NothingNewException {
		tracer.entering(Baseline.class.getSimpleName(), "create", new Object[]{basename, component, view, labelBehaviour, identical, activities, depends} );

		/* Remove prefixed baseline: */
		if( basename.toLowerCase().startsWith( "baseline:" ) ) {
			basename = basename.replaceFirst( "baseline:", "" );
		}

		boolean created = false; // context.createBaseline( basename, component, view, incremental, identical, activities, depends );

		String cmd = "mkbl -nc -component " + component.getNormalizedName() + ( identical ? " -identical" : "" );

		if( depends != null && depends.size() > 0 ) {
			cmd += " -adepends_on ";
			for( Component c : depends ) {
				cmd += c.getNormalizedName() + ",";
			}
			cmd = cmd.substring( 0, ( cmd.length() - 1 ) );
		}

		if( activities != null ) {
			cmd += " -activities";
			for( Activity a : activities ) {
				cmd += " " + a.getFullyQualifiedName() + ",";
			}
			cmd = cmd.substring( 0, ( cmd.length() - 1 ) );
		}

		cmd += " " + labelBehaviour.toArgument();
		cmd += " " + basename;

		try {
			String out = "";
			if( view != null ) {
				out = Cleartool.run( cmd, view ).stdoutBuffer.toString();
			} else {
				out = Cleartool.run( cmd ).stdoutBuffer.toString();
			}
			logger.debug( "Baseline output: " + out );

			created = out.matches( "(?s).*Created baseline \".*?\" in component \".*?\".*" ); // Created baseline
		} catch( AbnormalProcessTerminationException e ) {
			throw new UnableToCreateEntityException( Baseline.class, e );
		}

		if( created ) {
			Baseline baseline = get( basename, component.getPVob() );

			tracer.exiting(Baseline.class.getSimpleName(), "create", baseline);

			return baseline;
		} else {
			NothingNewException exception = new NothingNewException( "No baseline created, nothing new." );

			tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

			throw exception;
		}
	}

	/**
	 * Return the promotion level of a baseline. <br>
	 * If <code>cached</code> is not set, the promotion level is loaded from
	 * ClearCase.
	 * 
	 * @param cached
	 *            Whether to use the cached promotion level or not
	 * @return The promotion level of the Baseline
	 * @throws UnableToLoadEntityException
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Project.PromotionLevel getPromotionLevel( boolean cached ) {
		tracer.entering(Baseline.class.getSimpleName(), "getPromotionLevel", cached);

		//TODO if !loaded return this.plevel DONE.....
		if( cached ) {
			tracer.exiting(Baseline.class.getSimpleName(), "getPromotionLevel", this.plevel);

			return this.plevel;
		} else {
			/* TODO Get from clear case, uses cached value */
			/* If different from cached, cache the new */
			tracer.exiting(Baseline.class.getSimpleName(), "getPromotionLevel", this.plevel);

			return this.plevel;
		}
	}

	/**
	 * Promote the Baseline.
	 * <ul>
	 * <li><code>INITIAL -> BUILT</code></li>
	 * <li><code>BUILD&nbsp;&nbsp; -> TESTED</code></li>
	 * <li><code>TESTED&nbsp; -> RELEASED</code></li>
	 * </ul>
	 * 
	 * If the promotion level is not set, it is set to <code>INITAL</code>.
	 * 
	 * @return The new promotion level.
	 * @throws UnableToLoadEntityException
	 * @throws UnableToPromoteBaselineException
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Project.PromotionLevel promote() throws UnableToPromoteBaselineException {
		tracer.entering(Baseline.class.getSimpleName(), "promote");

		if( !loaded ) {
			tracer.finest("Not loaded.");
			try {
				load();
			} catch( ClearCaseException e ) {
				EntityNotLoadedException exception = new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );

				tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

				throw exception;
			}
		}

		if( this.plevel.equals( PromotionLevel.REJECTED ) ) {
			//throw new UCMException("Cannot promote from REJECTED");
			UnableToPromoteBaselineException exception = new UnableToPromoteBaselineException( this, PromotionLevel.REJECTED );

			tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

			throw exception;
		}

		this.plevel = Project.promoteFrom( this.plevel );
		setPromotionLevel( this.plevel );

		tracer.entering(Baseline.class.getSimpleName(), "promote", this.plevel);

		return this.plevel;
	}

	/**
	 * Demotes the Baseline to <code>REJECTED</code>.
	 * 
	 * @throws UnableToLoadEntityException
	 * @throws UnableToPromoteBaselineException
	 * @throws UCMEntityNotFoundException 
	 * @throws UnableToCreateEntityException 
	 * @throws UnableToGetEntityException 
	 */
	public Project.PromotionLevel demote() throws UnableToPromoteBaselineException {
		tracer.entering(Baseline.class.getSimpleName(), "demote");

		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				EntityNotLoadedException exception = new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );

				tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

				throw exception;
			}
		}

		this.plevel = Project.PromotionLevel.REJECTED;
		setPromotionLevel( this.plevel );

		PromotionLevel output = Project.PromotionLevel.REJECTED;

		tracer.exiting(Baseline.class.getSimpleName(), "demote", output);

		return output;
	}

	public void setPromotionLevel( Project.PromotionLevel plevel ) throws UnableToPromoteBaselineException {
		tracer.entering(Baseline.class.getSimpleName(), "setPromotionLevel", plevel);

		this.plevel = plevel;

		String cmd = "chbl -level " + plevel + " " + this;
		try {
			Cleartool.run( cmd );
		} catch( AbnormalProcessTerminationException e ) {
			UnableToPromoteBaselineException exception = new UnableToPromoteBaselineException( this, this.plevel );

			tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

			throw exception;
		}

		tracer.exiting(Baseline.class.getSimpleName(), "demote", this.plevel);
	}

	/**
	 * Get the differences between two Baselines.<br>
	 * Currently this method only support the previous Baseline and with -nmerge
	 * set.<br>
	 * 
	 * @return A BaselineDiff object containing a set of Activities.
	 * @throws UnableToGetEntityException 
	 */
	/*
	public BaselineDiff getDifferences( SnapshotView view ) {
		return new BaselineDiff( view, this );
	}
	 */

	public Component getComponent() {
		tracer.entering(Baseline.class.getSimpleName(), "getComponent");

		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				EntityNotLoadedException exception = new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );

				tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

				throw exception;
			}
		}

		tracer.exiting(Baseline.class.getSimpleName(), "getComponent", this.component);

		return this.component;
	}

	public Stream getStream() {
		tracer.entering(Baseline.class.getSimpleName(), "getStream");

		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				EntityNotLoadedException exception = new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );

				tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

				throw exception;
			}
		}

		tracer.exiting(Baseline.class.getSimpleName(), "getComponent", this.stream);

		return this.stream;
	}

	public boolean shouldResetMastership() throws ClearCaseException {
		tracer.entering(Baseline.class.getSimpleName(), "shouldResetMastership");

		boolean output = true;
		if( !getMastership().equals( this.getStream().getOriginalMastership() ) ) {
			tracer.exiting(Baseline.class.getSimpleName(), "shouldResetMastership", output);

			return output;
		}
		output = false;

		tracer.exiting(Baseline.class.getSimpleName(), "shouldResetMastership", output);

		return output;
	}

	public void resetMastership() throws ClearCaseException {
		tracer.entering(Baseline.class.getSimpleName(), "resetMastership");

		this.setMastership( this.getStream().getOriginalMastership() );

		tracer.exiting(Baseline.class.getSimpleName(), "resetMastership");
	} 

	public String stringify() {
		tracer.entering(Baseline.class.getSimpleName(), "stringify");

		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				EntityNotLoadedException exception = new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );

				tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));

				throw exception;
			}
		}

		StringBuffer sb = new StringBuffer();

		try {
			if( !this.loaded ) load();

			sb.append( " * Level    : " + this.plevel + linesep );
			sb.append( " * Component: " + this.component.toString() + linesep );
			sb.append( " * Stream   : " + this.stream.toString() + linesep );
			sb.append( " * Date     : " + this.date.toString() + linesep );

		} catch( Exception e ) {

		} finally {
			sb.insert( 0, super.stringify() );
		}

		String output = sb.toString();

		tracer.exiting(Baseline.class.getSimpleName(), "stringify", output);

		return output;
	}

	public static Baseline get( String name ) throws UnableToInitializeEntityException {
		tracer.entering(Baseline.class.getSimpleName(), "get", name);
		
		if( !name.startsWith( "baseline:" ) ) {
			name = "baseline:" + name;
		}
		Baseline entity = (Baseline) UCMEntity.getEntity( Baseline.class, name );
		
		tracer.exiting(Baseline.class.getSimpleName(), "get", entity);
		
		return entity;
	}

	public static Baseline get( String name, PVob pvob ) throws UnableToInitializeEntityException {
		tracer.entering(Baseline.class.getSimpleName(), "get", new Object[]{name, pvob});
		
		if( !name.startsWith( "baseline:" ) ) {
			name = "baseline:" + name;
		}
		Baseline entity = (Baseline) UCMEntity.getEntity( Baseline.class, name + "@" + pvob );
		
		tracer.exiting(Baseline.class.getSimpleName(), "get", entity);
		
		return entity;
	}

	public List<Baseline> getPostedBaselinesFor(Component component) throws UnableToInitializeEntityException, UnableToListBaselinesException, UnableToLoadEntityException {
		tracer.entering(Baseline.class.getSimpleName(), "getPostedBaselinesFor", component);
		logger.debug( "Getting posted baselines for " + this.getFullyQualifiedName() + " and " + component.getFullyQualifiedName() );
		List<String> bls_str = null;

		String cmd = "des -fmt %X[member_of_closure]p " + this.getFullyQualifiedName();
		//"lsbl -s -component " + component + " -stream " + stream + ( plevel != null ? " -level " + plevel.toString() : "" );
		try {
			bls_str = Cleartool.run( cmd, null, false ).stdoutList;
		} catch( AbnormalProcessTerminationException e ) {
			UnableToListBaselinesException exception = new UnableToListBaselinesException(getStream(), component, getPromotionLevel(true), e );
			
			tracer.severe(String.format("Exception thrown type: %s; message: %s", exception.getClass(), exception.getMessage()));
			
			throw exception;
		}

		List<Baseline> bls = new ArrayList<Baseline>();

		for( String bl_lines : bls_str ) {
			logger.debug( "Baselines " + bl_lines );
			String[] baselines = bl_lines.split(" ");
			logger.debug( "I got " + baselines.length + " baselines." );
			for(String bl : baselines ) {
				logger.debug( "Baseline " + bl );
				Baseline b = Baseline.get( bl ).load();
				logger.debug( "Baseline " + b.getFullyQualifiedName() + " component " + b.getComponent().getFullyQualifiedName() );
				logger.debug( "Component " + component.getFullyQualifiedName());
				logger.debug( "Baseline component " + b.getComponent().getFullyQualifiedName());
				if( b.getComponent().equals(component) ) 
					bls.add( b );
			}
		}

		tracer.exiting(Baseline.class.getSimpleName(), "getPostedBaselinesFor", bls);
		
		return bls;
	}

}
