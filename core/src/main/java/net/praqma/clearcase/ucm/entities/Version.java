package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.Cool;
import net.praqma.clearcase.PVob;
import net.praqma.clearcase.changeset.ChangeSet2;
import net.praqma.clearcase.cleartool.Cleartool;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.CleartoolException;
import net.praqma.clearcase.exceptions.EntityNotLoadedException;
import net.praqma.clearcase.exceptions.UCMEntityNotFoundException;
import net.praqma.clearcase.exceptions.UnableToCreateEntityException;
import net.praqma.clearcase.exceptions.UnableToGetEntityException;
import net.praqma.clearcase.exceptions.UnableToInitializeEntityException;
import net.praqma.clearcase.exceptions.UnableToLoadEntityException;
import net.praqma.clearcase.interfaces.Diffable;
import net.praqma.clearcase.ucm.view.SnapshotView;
import net.praqma.util.debug.Logger;
import net.praqma.util.execute.AbnormalProcessTerminationException;
import net.praqma.util.execute.CmdResult;
import net.praqma.util.execute.CommandLineInterface.OperatingSystem;

public class Version extends UCMEntity implements Comparable<Version> {
private static java.util.logging.Logger tracer = java.util.logging.Logger.getLogger(Config.GLOBAL_LOGGER_NAME);
	
	private static final Pattern rx_extendedName = Pattern.compile( "^(?:(" + rx_ccdef_filename + "+)@@)(?:(" + rx_ccdef_filename + "+)@@)?(.+)$" );
	private static final Pattern rx_checkExistence = Pattern.compile( ".*?Entry named \".*\" already exists.*?" );
	private static final Pattern rx_versionName = Pattern.compile( "^(\\S+)\\s+([\\S\\s.^@]+@@.*)$" );
	
	transient private static Logger logger = Logger.getLogger();
	
	//private String date = null;
	private String user = null;
	private String machine = null;
	private boolean checkedout = false;
	private String comment = null;
	private String branch = null;
	
	private boolean oldVersion = false;
	private File oldFile;

	private File view = null;

	private String fullfile = null;
	private String sfile = null;
	private File file = null;
	private String version = "";
	
	private Integer revision = 0;

	private static String rx_revision = "(\\d+)$";
	private static Pattern p_revision = Pattern.compile( "@@(.*)$" );
	
	public enum Status {
		UNCHANGED,
		CHANGED,
		ADDED,
		DELETED
	}
	
	private Status status = Status.UNCHANGED;

	Version() {
		super( "version" );
	}

	private static final Pattern rx_findAddedElements = Pattern.compile( qfs + ".*?" + qfs + "(\\d+)" + qfs + "(.*?)" + qfs );
	//private static final Pattern rx_findRevision = Pattern.compile( qfs + "(\\d+)$" );
	private static final Pattern rx_findRevision = Pattern.compile( "^(.*?)" + qfsor + "(\\d+)$" );

	@Override
	protected void initialize() {
tracer.entering(Version.class.getSimpleName(), "initialize");
		Matcher match = pattern_version_fqname.matcher( fqname );
		if( match.find() ) {
			/* Set the Entity variables */
			shortname = match.group( 1 );
			pvob = new PVob( match.group( 2 ) );
		}
		
		String fqname = "";
		if( Cool.getOS().equals( OperatingSystem.WINDOWS ) ) {
			fqname = this.fqname.matches( "^\\S:\\\\.*" ) ? this.fqname : System.getProperty( "user.dir" ) + filesep + this.fqname;
		} else {
			fqname = this.fqname.startsWith( "/" ) ? this.fqname : System.getProperty( "user.dir" ) + filesep + this.fqname;
		}

		this.fqname = fqname;

		/* Find the revision number, make it zero if it is not found */
		Matcher m = p_revision.matcher( this.fqname );
		if( m.find() ) {
			// this.revision = Integer.parseInt( m.group( 1 ) );
			this.version = m.group( 1 );
		} else {
			this.version = "0";
		}
		
		String tmp = this.fqname;
		tmp = tmp.replaceFirst( "(?m)@@.*$", "" );
		tmp = tmp.replaceFirst( "(?m)^\\s+", "" );
		this.fullfile = tmp.trim();
		
		/* Check if this is a newly added element
		 * Ie this is only shown as a parent folder change 
		 *  view\MonKit006\MonKit006\src@@\main\monkit006_1_dev\2\test\main\monkit006_1_dev\1\java\main\monkit006_1_dev\1
		 * */
		this.status = Status.CHANGED;
		Matcher ma = rx_findAddedElements.matcher( version );
		while( ma.find() ) {
			this.fullfile += filesep + ma.group(2);
		}

		this.file = new File( this.fullfile );
		Matcher r = rx_findRevision.matcher( this.version );
		if( r.find() ) {
			this.revision = Integer.parseInt( r.group(2) );
			if( this.revision == 1 ) {
				this.status = Status.ADDED;
			}
			
			/* Set the branch */
			this.branch = r.group( 1 );
			
			logger.debug( "REVISION: " + revision );
			logger.debug( "BRANCH: " + branch );
		}
tracer.exiting(Version.class.getSimpleName(), "initialize");
	}

	public boolean hijack() {
tracer.entering(Version.class.getSimpleName(), "hijack");
		if( this.file.canWrite() ) {
tracer.exiting(Version.class.getSimpleName(), "hijack", true);
			return true;
		}

tracer.exiting(Version.class.getSimpleName(), "hijack", this.file.setWritable( true ));
		return this.file.setWritable( true );
	}

	/* Getters */

	public static Version getUnextendedVersion( File file, File viewroot ) throws IOException, CleartoolException, UnableToLoadEntityException, UCMEntityNotFoundException, UnableToInitializeEntityException {
tracer.entering(Version.class.getSimpleName(), "getUnextendedVersion", new Object[]{file, viewroot});
tracer.exiting(Version.class.getSimpleName(), "getUnextendedVersion", context.getVersionExtension( file, viewroot ));
		//return context.getVersionExtension( file, viewroot );
		
		if( !file.exists() ) {
			throw new IOException( "The file " + file + " does not exist." );
		}

		String cmd = "desc -fmt %Xn " + file;
		String f = "";
		try {
			CmdResult r = Cleartool.run( cmd, viewroot );
			f = r.stdoutBuffer.toString();
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Unable to get extended version: " + e.getMessage(), e );
		}
		
tracer.exiting(Version.class.getSimpleName(), "getUnextendedVersion", (Version) UCMEntity.getEntity( Version.class, f ).load());
		return (Version) UCMEntity.getEntity( Version.class, f ).load();
	}

	public String blame() throws UCMEntityNotFoundException, UnableToCreateEntityException, UnableToGetEntityException, UnableToLoadEntityException {
tracer.entering(Version.class.getSimpleName(), "blame");
tracer.exiting(Version.class.getSimpleName(), "blame", this.getUser());
		return this.getUser();
	}

	public String getVersion() throws UnableToLoadEntityException {
tracer.entering(Version.class.getSimpleName(), "getVersion");
tracer.exiting(Version.class.getSimpleName(), "getVersion", this.version);
		return this.version;
	}

	public String getBranch() {
tracer.entering(Version.class.getSimpleName(), "getBranch");
tracer.exiting(Version.class.getSimpleName(), "getBranch", branch);
		return branch;
	}
	
	public Version load() throws UnableToLoadEntityException {
tracer.entering(Version.class.getSimpleName(), "load");
		try {
			String cmd = "describe -fmt %u}{%Vn}{%Xn}{%[object_kind]p \"" + this + "\"";
			String[] list = Cleartool.run( cmd ).stdoutBuffer.toString().split( "\\}\\{" );

			/* First line, user */
			setUser( list[0] );

			/* Second line, version name */
			String vn = list[1];

			/* Third line, version extended name */
			String ven = list[2];
			Matcher m = rx_extendedName.matcher( ven );

			if( list[3].equals( "file element" ) ) {
				setKind( Kind.FILE_ELEMENT );
			} else if( list[3].equals( "directory version" ) ) {
				setKind( Kind.DIRECTORY_ELEMENT );
			}

		} catch( Exception e ) {
			throw new UnableToLoadEntityException( this, e );
		}
		
tracer.exiting(Version.class.getSimpleName(), "load", this);
		return this;
	}
	
	public static Version create( File file, boolean mkdir, SnapshotView view ) throws CleartoolException, IOException, UnableToCreateEntityException, UCMEntityNotFoundException, UnableToGetEntityException, UnableToLoadEntityException, UnableToInitializeEntityException {
tracer.entering(Version.class.getSimpleName(), "create", new Object[]{file, mkdir, view});

		Version.addToSourceControl( file, mkdir, view.getViewRoot() );
		
		Version version = Version.getUnextendedVersion( file, view.getViewRoot() );
		version.setView( view );
		
tracer.exiting(Version.class.getSimpleName(), "create", version);
		return version;
	}
	
	/**
	 * Create a ClearCase element from a File, that will be checked in
	 * @param file - The relative file
	 * @param viewContext - The view root
	 * @return
	 * @throws ClearCaseException
	 * @throws IOException
	 */
	public static Version create( File file, File viewContext ) throws ClearCaseException, IOException {
tracer.entering(Version.class.getSimpleName(), "create", new Object[]{file, viewContext});

		Version.addToSourceControl( file, viewContext, null, true );
		
		Version version = Version.getUnextendedVersion( file, viewContext );
		version.setView( viewContext );
		
tracer.exiting(Version.class.getSimpleName(), "create", version);
		return version;
	}
	
	public static void makeElement( File file, File view, String comment ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "makeElement", new Object[]{file, view, comment});
		String cmd = "mkelem " + ( comment != null ? "-c \"" + comment + "\" " : "" ) + file;
		
		try {
			Cleartool.run( cmd, view );
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to make element " + file, e );
		}
tracer.exiting(Version.class.getSimpleName(), "makeElement");
	}
	
	public static void makeDirectory( File directory, File view, String comment ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "makeDirectory", new Object[]{directory, view, comment});
		String cmd = "mkdir " + ( comment != null ? "-c \"" + comment + "\" " : "" ) + directory;
		
		try {
			Cleartool.run( cmd, view );
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to make directory " + directory, e );
		}
tracer.exiting(Version.class.getSimpleName(), "makeDirectory");
	}
	
	/**
	 * 
	 * @param file
	 * @param view
	 * @param viewContext
	 * @param checkIn
	 * @throws CleartoolException
	 */
	public static void addToSourceControl( File file, File viewContext, String comment, boolean checkIn ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "addToSourceControl", new Object[]{file, viewContext, comment, checkIn});
		String cmd = "mkelem -mkpath ";
		cmd += checkIn ? "-ci " : " ";
		cmd += comment != null ? "-comment \"" + comment + "\" " : "-nc ";
		cmd += file;
		
		try {
			Cleartool.run( cmd, viewContext );
		} catch( Exception e ) {
			throw new CleartoolException( "Could not add " + file + " to source control", e );
		}
tracer.exiting(Version.class.getSimpleName(), "addToSourceControl");
	}
	
	public static void addToSourceControl( File file, boolean mkdir, File view ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "addToSourceControl", new Object[]{file, mkdir, view});
			/* Check existence */
			List<File> files = new ArrayList<File>();
			File parent = file.getParentFile();
			logger.debug( "FILE  : " + file );
			logger.debug( "PARENT: " + parent );
			while( !parent.equals( view ) ) {
				files.add( parent );
				parent = parent.getParentFile();
			}

			for( int i = files.size() - 1; i >= 0; i-- ) {
				String cmd = "mkdir " + files.get( i ).getPath();
				try {
					/* The parent must be checked out before adding elements */
					try {
						checkOut( files.get( i ).getParentFile(), view );
					} catch( CleartoolException e ) {
						/* This probably indicates the directory is checked out */
					}
					Cleartool.run( cmd, view );
				} catch( Exception e ) {
				}
			}

			try {
				/* Check out the folder */
				try {
					checkOut( file.getParentFile(), view );
				} catch( CleartoolException e ) {
					/* Maybe it is checked out? */
				}

				/* Determine whether the File is a file or a directory */
				String cmd = "";
				if( mkdir ) {
					cmd = "mkdir -nc " + file;
				} else {
					cmd = "mkelem -nc " + file;
				}
				Cleartool.run( cmd, view );
			} catch( Exception e ) {
				/* Already added to source control */
				logger.debug( "---->" + e.getMessage() );
				Matcher m = rx_checkExistence.matcher( e.getMessage() );
				if( m.find() ) {
					logger.debug( file + " already added to source control" );
tracer.exiting(Version.class.getSimpleName(), "addToSourceControl");
					return;
				}

				throw new CleartoolException( "Could not add " + file + " to source control", e );
			}

		}
	
	public void checkIn() throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkIn");
		//context.checkIn( this, false, view.getViewRoot() );
		checkIn( file, false, view );
tracer.exiting(Version.class.getSimpleName(), "checkIn");
	}
	
	public static void checkIn( File file, boolean identical, File viewContext ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkIn", new Object[]{file, identical, viewContext});
		checkIn( file, identical, viewContext, null );
tracer.exiting(Version.class.getSimpleName(), "checkIn");
	}
	
	public static void checkIn( File file, boolean identical, File viewContext, String comment ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkIn", new Object[]{file, identical, viewContext, comment});
		try {
			String cmd = "checkin " + ( comment != null ? "-c \"" + comment + "\" " : "-nc " ) + ( identical ? "-identical " : "" ) + file;
			Cleartool.run( cmd, viewContext, true, false );
		} catch( Exception e ) {
			if( e.getMessage().matches( "(?s).*By default, won't create version with data identical to predecessor.*" ) ) {
				logger.debug( "Identical version, trying to uncheckout" );
				uncheckout( file, false, viewContext );
tracer.exiting(Version.class.getSimpleName(), "checkIn");
				return;
			} else {
				throw new CleartoolException( "Could not check in", e );
			}

		}
	}
	
	public void checkIn( boolean identical ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkIn", new Object[]{identical});
		checkIn( this.getFile(), identical, view );
tracer.exiting(Version.class.getSimpleName(), "checkIn");
	}
	
	public void checkInIdentical() throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkInIdentical");
		checkIn( this.getFile(), true, view );
tracer.exiting(Version.class.getSimpleName(), "checkInIdentical");
	}
	
	public void checkOut() throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkOut");
		checkOut( this.getFile(), view );
tracer.exiting(Version.class.getSimpleName(), "checkOut");
	}
	
	public static void checkOut( File file, File context ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkOut", new Object[]{file, context});
		checkOut( file, context, null );
tracer.exiting(Version.class.getSimpleName(), "checkOut");
	}
	
	public static void checkOut( File file, File context, String comment ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "checkOut", new Object[]{file, context, comment});
		try {
			String cmd = "checkout " + ( comment != null ? "-c \"" + comment + "\" " : "-nc " ) + file;
			Cleartool.run( cmd, context );
		} catch( Exception e ) {
			throw new CleartoolException( "Could not check out " + file, e );
		}
tracer.exiting(Version.class.getSimpleName(), "checkOut");
	}
	
	public void removeVersion() throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "removeVersion");
		removeVersion( this.file, view );
tracer.exiting(Version.class.getSimpleName(), "removeVersion");
	}
	
	public static void removeVersion( File file, File viewContext ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "removeVersion", new Object[]{file, viewContext});
		/* Firstly, checkout directory */
		try {
			checkOut( file.getParentFile(), viewContext );
		} catch( CleartoolException e ) {
			/*
			 * The file is probably already checked out, let's try to continue
			 */
		}

		String cmd = "rmver -force -xlabel -xattr -xhlink " + file;

		try {
			uncheckout( file, false, viewContext );
		} catch( CleartoolException e ) {
			/* Could not uncheckout */
			logger.warning( "Could not uncheckout " + file );
		}

		try {
			Cleartool.run( cmd, viewContext );
		} catch( Exception e ) {
			throw new CleartoolException( "Could not remove " + file + ": " + e.getMessage(), e );
		}
tracer.exiting(Version.class.getSimpleName(), "removeVersion");
	}
	
	public void removeName( ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "removeName");
		removeName( this.file, view );
tracer.exiting(Version.class.getSimpleName(), "removeName");
	}
	
	public static void removeName( File file, File context ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "removeName", new Object[]{file, context});
		/* Firstly, checkout directory */
		try {
			checkOut( file.getParentFile(), context );
		} catch( CleartoolException e ) {
			/*
			 * The file is probably already checked out, let's try to continue
			 */
		}

		try {
			uncheckout( file, false, context );
		} catch( CleartoolException e ) {
			/* Could not uncheckout */
			logger.debug( "Could not uncheckout " + file );
		}

		try {
			// String cmd = "rmname -force " + ( checkedOut ? "" : "-nco " ) +
			// file;
			String cmd = "rmname -force -nco " + file;
			Cleartool.run( cmd, context );
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to remove name " + file + " at " + context, e );
		}
tracer.exiting(Version.class.getSimpleName(), "removeName");
	}
	
	public static void moveFile( File file, File destination, File context ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "moveFile", new Object[]{file, destination, context});
		try {
			try {
				checkOut( file.getParentFile(), context );
			} catch( CleartoolException e ) {
				/* Directory could be checked out already, let's proceed */
			}

			/*
			 * If destination is a directory and NOT the same as the source,
			 * let's try to check it out
			 */
			if( destination.isDirectory() && !file.getParentFile().equals( destination ) ) {
				try {
					checkOut( destination, context );
				} catch( CleartoolException e ) {
					/* Directory could be checked out already, let's proceed */
				}
				/*
				 * If destination is a file and its directory is NOT the same as
				 * the source, then try to checkout the directory
				 */
			} else if( destination.isFile() && !destination.getParentFile().equals( file.getParentFile() ) ) {
				try {
					checkOut( destination.getParentFile(), context );
				} catch( CleartoolException e ) {
					/* Directory could be checked out already, let's proceed */
				}
			}

			String cmd = "mv " + file + " " + destination;
			Cleartool.run( cmd, context );
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to move " + file + " to " + destination, e );
		}
tracer.exiting(Version.class.getSimpleName(), "moveFile");
	}
	
	public void moveFile( File destination ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "moveFile", new Object[]{destination});
		moveFile( file, destination, view );
tracer.exiting(Version.class.getSimpleName(), "moveFile");
	}
	
	public void uncheckout() throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "uncheckout");
		uncheckout( this.getFile(), true, view );
tracer.exiting(Version.class.getSimpleName(), "uncheckout");
	}
	
	public void uncheckout( boolean keep ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "uncheckout", new Object[]{keep});
		uncheckout( this.getFile(), keep, view );
tracer.exiting(Version.class.getSimpleName(), "uncheckout");
	}
	
	public static void uncheckout( File file, boolean keep, File viewContext ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "uncheckout", new Object[]{file, keep, viewContext});
		try {
			String cmd = "uncheckout -rm " + ( keep ? "-rm " : "" ) + file;
			Cleartool.run( cmd, viewContext );
		} catch( Exception e ) {
			throw new CleartoolException( "Could not uncheck out", e );
		}
tracer.exiting(Version.class.getSimpleName(), "uncheckout");
	}
	
	public static void recursiveCheckin( File path ) {
tracer.entering(Version.class.getSimpleName(), "recursiveCheckin", new Object[]{path});
		try {
			List<File> files = Version.getUncheckedIn( path );
			for( File f : files ) {
				logger.debug( "Checking in " + f );
				try {
					Version.checkIn( f, false, path );
				} catch( CleartoolException e1 ) {
					logger.debug( "Unable to checkin " + f );
					/* No op */
				}
			}
		} catch( CleartoolException e1 ) {
			logger.error( e1.getMessage() );				
		}
tracer.exiting(Version.class.getSimpleName(), "recursiveCheckin");
	}

	public void setView( SnapshotView view ) {
tracer.entering(Version.class.getSimpleName(), "setView", new Object[]{view});
		this.view = view.getViewRoot();
tracer.exiting(Version.class.getSimpleName(), "setView");
	}
	
	public void setView( File view ) {
tracer.entering(Version.class.getSimpleName(), "setView", new Object[]{view});
		this.view = view;
tracer.exiting(Version.class.getSimpleName(), "setView");
	}
	
	public File getView() {
tracer.entering(Version.class.getSimpleName(), "getView");
tracer.exiting(Version.class.getSimpleName(), "getView", view);
		return view;
	}

	public void setSFile( String sfile ) {
tracer.entering(Version.class.getSimpleName(), "setSFile", new Object[]{sfile});
		this.sfile = sfile;
tracer.exiting(Version.class.getSimpleName(), "setSFile");
	}

	public String getSFile() {
tracer.entering(Version.class.getSimpleName(), "getSFile");
tracer.exiting(Version.class.getSimpleName(), "getSFile", sfile);
		return sfile;
	}
	
	public void setFile( File file ) {
tracer.entering(Version.class.getSimpleName(), "setFile", new Object[]{file});
		this.file = file;
tracer.exiting(Version.class.getSimpleName(), "setFile");
	}
	
	public File getFile() {
tracer.entering(Version.class.getSimpleName(), "getFile");
tracer.exiting(Version.class.getSimpleName(), "getFile", file);
		return file;
	}
	
	public void setStatus( Status status ) {
tracer.entering(Version.class.getSimpleName(), "setStatus", new Object[]{status});
		this.status = status;
tracer.exiting(Version.class.getSimpleName(), "setStatus");
	}
	
	public Status getStatus() {
tracer.entering(Version.class.getSimpleName(), "getStatus");
tracer.exiting(Version.class.getSimpleName(), "getStatus", status);
		return status;
	}
	
	public Integer getRevision() {
tracer.entering(Version.class.getSimpleName(), "getRevision");
tracer.exiting(Version.class.getSimpleName(), "getRevision", this.revision);
		return this.revision;
	}
	
	public static List<File> getUncheckedIn( File viewContext ) throws CleartoolException {
		List<File> files = new ArrayList<File>();

		try {
			File[] vobs = viewContext.listFiles();
			for( File vob : vobs ) {
				logger.debug( "Checking " + vob );
				if( !vob.isDirectory() || vob.getName().matches( "^\\.{1,2}$" ) ) {
					continue;
				}
				logger.debug( vob + " is a valid vob" );

				String cmd = "lsco -s -r";
				List<String> list = Cleartool.run( cmd, vob ).stdoutList;

				for( String s : list ) {
					files.add( new File( vob, s ) );
tracer.exiting(Version.class.getSimpleName(), "File");
tracer.entering(Version.class.getSimpleName(), "File", new Object[]{s});
				}
			}

			return files;

		} catch( Exception e ) {
			throw new CleartoolException( "Could not retreive files", e );
tracer.exiting(Version.class.getSimpleName(), "CleartoolException");
tracer.entering(Version.class.getSimpleName(), "CleartoolException", new Object[]{not, files"});
		}
	}
	
	public boolean isDirectory() throws UnableToLoadEntityException {
tracer.entering(Version.class.getSimpleName(), "isDirectory");
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
tracer.exiting(Version.class.getSimpleName(), "isDirectory", kind.equals( Kind.DIRECTORY_ELEMENT ));
		return kind.equals( Kind.DIRECTORY_ELEMENT );
	}
	
	public boolean isFile() throws UnableToLoadEntityException {
tracer.entering(Version.class.getSimpleName(), "isFile");
		if( !loaded ) {
			try {
				load();
			} catch( ClearCaseException e ) {
				throw new EntityNotLoadedException( fqname, fqname + " could not be auto loaded", e );
			}
		}
		
tracer.exiting(Version.class.getSimpleName(), "isFile", kind.equals( Kind.FILE_ELEMENT ));
		return kind.equals( Kind.FILE_ELEMENT );
	}

	
	/**
	 * An exception safe way to determine whether the file is under
	 * source control
	 * @param element The File to be checked
	 * @param viewContext The view context as a File path
	 * @return True if the File element is under source control
	 * @throws CleartoolException 
	 */
	public static boolean isUnderSourceControl( File element, File viewContext ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "isUnderSourceControl", new Object[]{element, viewContext});
		String cmd = "describe " + element;
		try {
			String line = Cleartool.run( cmd, viewContext ).stdoutBuffer.toString();

			if( line.contains( "View private file" ) ) {
tracer.exiting(Version.class.getSimpleName(), "isUnderSourceControl", false);
				return false;
			} else {
tracer.exiting(Version.class.getSimpleName(), "isUnderSourceControl", true);
				return true;
			}
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to determine whether " + element + " is under source control or not", e );
		}
	}
	
	/**
	 * An exception safe way to determine whether the file is checked out
	 * @param element The File to be checked
	 * @param viewContext The view context as a File path
	 * @return True if the File element is checked out
	 * @throws CleartoolException 
	 */
	public static boolean isCheckedout( File element, File viewContext ) throws CleartoolException {
tracer.entering(Version.class.getSimpleName(), "isCheckedout", new Object[]{element, viewContext});
		String cmd = "describe -s " + element;
		try {
			String line = Cleartool.run( cmd, viewContext ).stdoutBuffer.toString();

			if( line.endsWith( "\\CHECKEDOUT" ) ) {
tracer.exiting(Version.class.getSimpleName(), "isCheckedout", true);
				return true;
			} else {
tracer.exiting(Version.class.getSimpleName(), "isCheckedout", false);
				return false;
			}
		} catch( Exception e ) {
			throw new CleartoolException( "Unable to determine whether " + element + " is checked out or not", e );
		}
	}
	
	public void setOldFile( File oldFile ) {
tracer.entering(Version.class.getSimpleName(), "setOldFile", new Object[]{oldFile});
		this.oldFile = oldFile;
tracer.exiting(Version.class.getSimpleName(), "setOldFile");
	}
	
	public boolean isMoved() {
tracer.entering(Version.class.getSimpleName(), "isMoved");
tracer.exiting(Version.class.getSimpleName(), "isMoved", ( oldFile != null ));
		return ( oldFile != null );
	}

	public String stringify() {
tracer.entering(Version.class.getSimpleName(), "stringify");
		StringBuffer sb = new StringBuffer();

		try {
			if( !this.loaded ) load();

			sb.append( super.stringify() );
			sb.append( super.stringify() + linesep );

			sb.append( "Filename: " + this.fullfile + linesep );
			sb.append( "Revision: " + this.version + linesep );
		} catch( Exception e ) {

		} finally {
			//sb.append( super.stringify() );
			sb.insert( 0, super.stringify() );
		}

tracer.exiting(Version.class.getSimpleName(), "stringify", sb.toString());
		return sb.toString();
	}
	
	public static ChangeSet2 getChangeset( Diffable e1, Diffable e2, boolean merge, File viewContext ) throws CleartoolException, UnableToInitializeEntityException {
tracer.entering(Version.class.getSimpleName(), "getChangeset", new Object[]{e1, e2, merge, viewContext});
tracer.exiting(Version.class.getSimpleName(), "getChangeset", context.getChangeset( e1, e2, merge, viewContext ));
		//return context.getChangeset( e1, e2, merge, viewContext );
		String cmd = "diffbl -version " + ( !merge ? "-nmerge " : "" ) + ( e2 == null ? "-pre " : "" ) + " " + e1.getFullyQualifiedName() + ( e2 != null ? e2.getFullyQualifiedName() : "" );

		List<String> lines = null;

		try {
			lines = Cleartool.run( cmd, viewContext ).stdoutList;
		} catch( Exception e ) {
			throw new CleartoolException( "Could not retreive the differences of " + e1 + " and " + e2 + ": " + e.getMessage(), e );
		}

		int length = viewContext.getAbsoluteFile().toString().length();

		// System.out.println(viewContext.getAbsolutePath() + " - " + length);

		net.praqma.clearcase.changeset.ChangeSet2 changeset = new ChangeSet2( viewContext );

		for( int i = 0; i < lines.size(); i++ ) {
			Matcher m = rx_versionName.matcher( lines.get( i ) );
			if( m.find() ) {

				String f = m.group( 2 ).trim();

				logger.debug( "F: " + f );

				Version version = (Version) UCMEntity.getEntity( Version.class, m.group( 2 ).trim() );

				changeset.addVersion( version );
			}
		}

tracer.exiting(Version.class.getSimpleName(), "getChangeset", changeset);
		return changeset;
	}
	
	public static List<Activity> getBaselineDiff( Diffable d1, Diffable d2, boolean merge, File viewContext ) throws CleartoolException, UnableToLoadEntityException, UCMEntityNotFoundException, UnableToInitializeEntityException {
		return getBaselineDiff( d1, d2, merge, viewContext, true );
tracer.exiting(Version.class.getSimpleName(), "getBaselineDiff", getBaselineDiff( d1, d2, merge, viewContext, true ));
tracer.entering(Version.class.getSimpleName(), "getBaselineDiff", new Object[]{d2, viewContext});
	}
	
	/**
	 * Activity based baseline diff method
	 * @param d1
	 * @param d2
	 * @param merge
	 * @param viewContext
	 * @param versions
	 * @return
	 * @throws CleartoolException
	 * @throws UnableToLoadEntityException
	 * @throws UCMEntityNotFoundException
	 * @throws UnableToInitializeEntityException
	 */
	public static List<Activity> getBaselineDiff( Diffable d1, Diffable d2, boolean merge, File viewContext, boolean versions ) throws CleartoolException, UnableToLoadEntityException, UCMEntityNotFoundException, UnableToInitializeEntityException {
		String cmd = "diffbl " + ( versions ? "-versions " : "" ) + " -activities " + ( !merge ? "-nmerge " : "" ) + ( d2 == null ? "-pre " : "" ) + d1.getFullyQualifiedName() + ( d2 != null ? " " + d2.getFullyQualifiedName() : "" );

		List<String> lines = null;
		
		try {
			lines = Cleartool.run( cmd, viewContext ).stdoutList;
		} catch( AbnormalProcessTerminationException e ) {
			throw new CleartoolException( "Could not get difference between " + d1.getFullyQualifiedName() + " and " + d2.getFullyQualifiedName() + ": " + e.getMessage(), e );
tracer.exiting(Version.class.getSimpleName(), "CleartoolException");
tracer.entering(Version.class.getSimpleName(), "CleartoolException", new Object[]{not, difference, ", d1.getFullyQualifiedName(), ", ", d2.getFullyQualifiedName(), ":, +, e});
		}
		
		logger.debug( "LINES: " + lines );
		
		return Activity.parseActivityStrings( lines, viewContext.getAbsoluteFile().toString().length() );
	}

	@Override
	public int compareTo( Version other ) {
tracer.entering(Version.class.getSimpleName(), "compareTo", new Object[]{other});
		/* The same file */
		if( this.file.equals( other.getFile() ) ) {
			try {
tracer.exiting(Version.class.getSimpleName(), "compareTo", this.version.compareTo( other.getVersion() ));
				return this.version.compareTo( other.getVersion() );
			} catch ( UnableToLoadEntityException e) {
tracer.exiting(Version.class.getSimpleName(), "compareTo", -1);
				return -1;
			}
		} else {
tracer.exiting(Version.class.getSimpleName(), "compareTo", this.file.compareTo( other.getFile() ));
			return this.file.compareTo( other.getFile() );
		}
	}


	public static Version get( String name ) throws UnableToInitializeEntityException {
tracer.entering(Version.class.getSimpleName(), "get", new Object[]{name});
tracer.exiting(Version.class.getSimpleName(), "get", (Version) UCMEntity.getEntity( Version.class, name ));
		return (Version) UCMEntity.getEntity( Version.class, name );
	}
	
	public static void printCheckouts( File viewContext ) {
tracer.entering(Version.class.getSimpleName(), "printCheckouts", new Object[]{viewContext});
		try {
			CmdResult result = Cleartool.run( "lsco -r", viewContext );
			logger.debug( "RESULT\\n" + result.stdoutBuffer );
		} catch( Exception ex ) {
			logger.warning( ex.getMessage() );
		}
tracer.exiting(Version.class.getSimpleName(), "printCheckouts");
	}
	
	public static Version getVersion( String version ) throws UnableToInitializeEntityException {
tracer.entering(Version.class.getSimpleName(), "getVersion", new Object[]{version});
tracer.exiting(Version.class.getSimpleName(), "getVersion", (Version) UCMEntity.getEntity( Version.class, version ));
		return (Version) UCMEntity.getEntity( Version.class, version );
	}
}
