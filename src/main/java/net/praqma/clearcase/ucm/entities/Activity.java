package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.PVob;
import net.praqma.clearcase.api.Describe;
import net.praqma.clearcase.api.DiffBl;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.*;
import net.praqma.util.execute.AbnormalProcessTerminationException;

public class Activity extends UCMEntity {

	private final static Pattern pattern_activity = Pattern.compile( "^[<>-]{2}\\s*(\\S+)\\s*.*$" );
    private final static Pattern pattern_activity2 = Pattern.compile( "^([<>-]{2})\\s*(\\S+)\\s*.*$" );
	
	private static final Logger logger = Logger.getLogger( Activity.class.getName() );

    /**
     * The change set of the activity
     */
	public Changeset changeset = new Changeset();

    //The specialCase field is used when parsing an activity string from cleartool, when there's no_activity
	private boolean specialCase = false;

    /**
     * The headline of the {@link Activity}
     */
	private String headline = "";

	Activity() {
		super( "activity" );
	}

	public void setSpecialCase( boolean b ) {
		this.specialCase = b;
	}

	public boolean isSpecialCase() {
		return this.specialCase;
	}

	/**
	 * Load the Activity into memory from ClearCase.<br>
	 * This function is automatically called when needed by other functions.
	 * @return The current {@link Activity} populated with ClearCase data.
	 * @throws UnableToLoadEntityException Thrown when ClearCase reports errors 
	 */
    @Override
	public Activity load() throws UnableToLoadEntityException {
		String[] result = new String[2];

		/* The special case branch */
		if( isSpecialCase() ) {
			result[0] = "System";
			result[1] = "";
		} else {
			String cmd = "describe -fmt %u{!}%[headline]p " + this;
			try {
				String line = Cleartool.run( cmd ).stdoutBuffer.toString();
                result = line.split( "\\{!\\}" );
			} catch( AbnormalProcessTerminationException e ) {
				throw new UnableToLoadEntityException( this, e );
			}
		}
		
		setUser( result[0].trim() );
		headline = result[1].trim();
        this.loaded = true;
		
		return this;
	}
	
	/**
	 * Create an activity. If name is null an anonymous activity is created and the return value is null.
	 * @param name The name of the activity
	 * @param in The stream in which to create the actitivy
	 * @param pvob Vob of the actitivy
	 * @param force Force creation
	 * @param comment Comment of the activity
	 * @param headline Headline for the activyt
	 * @param view Current view
	 * @return An {@link Activity} created by ClearCase.
	 * @throws UnableToCreateEntityException Thrown when ClearCase reports errors 
	 * @throws UCMEntityNotFoundException Thrown when ClearCase reports errors 
	 * @throws UnableToGetEntityException Thrown when ClearCase reports errors 
	 * @throws UnableToInitializeEntityException Thrown when ClearCase reports errors 
	 */
	public static Activity create( String name, Stream in, PVob pvob, boolean force, String comment, String headline, File view ) throws UnableToCreateEntityException, UCMEntityNotFoundException, UnableToGetEntityException, UnableToInitializeEntityException {
		String cmd = "mkactivity" + ( comment != null ? " -c \"" + comment + "\"" : " -nc" ) + 
									( headline != null ? " -headline \"" + headline + "\"" : "" ) +
									( in != null ? " -in " + in.getNormalizedName() : "" ) + 
									( force ? " -force" : "" ) + 
									( name != null ? " " + name + "@" + pvob : "" );

		try {
            System.out.println("Cleartool-run");
            System.out.println("Running this command: "+cmd );
            System.out.println("In this view: "+view);
			Cleartool.run( cmd, view );
            System.out.println("Cleartool-run-done");
		} catch( Exception e ) {
            System.out.println("Cleartool-exception");
			throw new UnableToCreateEntityException( Activity.class, e );
		}
		
		Activity activity = null;
		System.out.println("Cleartool-before-namenullcheck");
		if( name != null ) {
            System.out.println("Getting...");
			activity = get( name, pvob );
            System.out.println("Done getting...");
		}		
		return activity;
	}

	public static List<Activity> parseActivityStrings( List<String> result, File view ) throws UnableToLoadEntityException, UCMEntityNotFoundException, UnableToInitializeEntityException {
        int length = view.getAbsoluteFile().toString().length();
		ArrayList<Activity> activities = new ArrayList<Activity>();
		Activity current = null;
		for( String s : result ) {
			/* Get activity */
			Matcher match = pattern_activity.matcher( s );

			/* This line is a new activity */
			if( match.find() ) {
				current = get( match.group( 1 ) );               
				/* A special case? */
				if( current.getShortname().equals( "no_activity" ) ) {
					logger.fine( "Recorded a special activity case" );
					current.setSpecialCase( true );
				}
				activities.add( current );
                current.load();
				continue;
			}

			if( current == null ) {
				logger.fine( "Not an activity: " + s );
				continue;
			}

			/* If not an activity, it must be a version */
			String f = s.trim();

			Version v = (Version) UCMEntity.getEntity( Version.class, f );
            v.setView(view);
            v.setActivity(current);            
			v.setSFile( v.getFile().getAbsolutePath().substring( length ) );

			current.changeset.versions.add( v );
		
		}

		return activities;
	}

    public static class Parser {
        public enum Direction {
            /** The item is present only in baseline-selector1 or stream-selector1. */
            LEFT( "<<" ),
            /** The activity is present in both of the items being compared and is newer in baseline-selector1 or stream-selector1. */
            LEFTI( "<-" ),
            /** The item is present only in baseline-selector2 or stream-selector2. */
            RIGHT( ">>" ),
            /** The activity is present in both of the items being compared and is newer in baseline-selector2 or stream-selector2. */
            RIGHTI( "->" );

            private String symbol;

            private Direction( String symbol ) {
                this.symbol = symbol;
            }

            public boolean matches( String symbol ) {
                return this.symbol.equals( symbol );
            }
        }

        private List<Direction> directions = new ArrayList<Direction>( 4 );

        private DiffBl diffBl;

        private boolean activityUserAsVersionUser = false;

        private int length = 0;

        private List<Activity> activities = new ArrayList<Activity>(  );

        public Parser( DiffBl diffBl ) {
            this.diffBl = diffBl;
            if( diffBl.getViewRoot() != null ) {
                length = diffBl.getViewRoot().getAbsoluteFile().toString().length();
            }
        }

        public List<Activity> getActivities() {
            return activities;
        }

        public Parser addDirection( Direction direction ) {
            this.directions.add( direction );

            return this;
        }

        public Parser setActivityUserAsVersionUser( boolean b ) {
            activityUserAsVersionUser = b;
            return this;
        }

        private boolean hasDirection( String symbol ) {
            for( Direction direction : directions ) {
                if( direction.matches( symbol ) ) {
                    return true;
                }
            }

            return false;
        }

        public Parser parse() throws ClearCaseException {
            Activity current = null;
            boolean include = false;

            List<String> lines = diffBl.execute();

            for( String line : lines ) {
                logger.finest( "Line: " + line );

			    /* Get activity */
                Matcher match = pattern_activity2.matcher( line );

                /* This line is a new activity */
                if( match.find() ) {
                    /* Test direction */
                    String symbol = match.group( 1 );
                    if( hasDirection( symbol ) ) {
                        current = get( match.group( 2 ) );

                        /* A special case? */
                        if( current.getShortname().equals( "no_activity" ) ) {
                            logger.fine( "Recorded a special activity case" );
                            current.setSpecialCase( true );
                        }
                        activities.add( current );
                        include = true;
                    } else {
                        include = false;
                    }

                    continue;
                }

                if( include ) {
                    if( current == null ) {
                        logger.fine( "Current is not an activity: " + line );
                        continue;
                    }

                    /* If not an activity, it must be a version */
                    String f = line.trim();

                    Version v = (Version) UCMEntity.getEntity( Version.class, f );                    
                    v.setSFile( v.getFile().getAbsolutePath().substring( length ) );
                    v.setView(diffBl.getViewRoot());
                    v.setActivity(current);
                    
                    if( activityUserAsVersionUser ) {
                        v.setUser( current.getUser() );
                    } else {
                        v.load();
                    }                    
                    
                    current.changeset.versions.add( v );
                }
            }

            return this;
        }
    }

    /**
     * Trim the change set to the latest.
     * @param branchName If given, only the {@link Version}s on this branch will be included.
     * @return A list of {@link Version}s trimmed.
     */
    public List<Version> getTrimmedChangeSet( String branchName ) {
        logger.log(Level.FINE, "Trimming change set to latest from {0}", branchName);

        Map<File, Version> versions = new HashMap<File, Version>(  );
        for( Version v : changeset.versions ) {
            if( branchName == null || v.getBranch().matches( branchName ) ) {
                if( versions.containsKey( v.getFile() ) ) {
                    if( v.getRevision() > versions.get( v.getFile() ).getRevision() ) {
                        versions.put( v.getFile(), v );
                    }
                } else {
                    versions.put( v.getFile(), v );
                }
            }
        }

        return new ArrayList( versions.values() );
    }
	
	public String getHeadline() {
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
		return headline;
	}

    public List<Version> getVersions( File path ) throws UnableToInitializeEntityException, CleartoolException {
        return getVersions( this, path );
    }

    public static List<Version> getVersions( Activity activity, File path ) throws UnableToInitializeEntityException {
        String output = null;
        try {
            output = new Describe( activity ).addModifier( Describe.versions ).setPath( path ).executeGetFirstLine();
        } catch( CleartoolException e ) {
            return Collections.emptyList();
        }

        String[] versionNames = output.split( "," );

        List<Version> versions = new ArrayList<Version>( versionNames.length );

        for( String versionName : versionNames ) {
            Version v = Version.get( versionName.trim() );
            v.setActivity(activity);
            versions.add(v);
        }

        return versions;
    }
	
	public static Activity get( String name ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "activity:" ) ) {
			name = "activity:" + name;
		}
		Activity entity = (Activity) UCMEntity.getEntity( Activity.class, name );
		return entity;
	}

	public static Activity get( String name, PVob pvob ) throws UnableToInitializeEntityException {
		if( !name.startsWith( "activity:" ) ) {
			name = "activity:" + name;
		}
		Activity entity = (Activity) UCMEntity.getEntity( Activity.class, name + "@" + pvob );
		return entity;
	}
	
}
