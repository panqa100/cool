package net.praqma.clearcase.ucm.entities;

import java.io.File;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.praqma.clearcase.ucm.UCMException;
import net.praqma.clearcase.ucm.view.SnapshotView;

public class Version extends UCMEntity {
	/* Version specific fields */
	private String kind = null;
	//private String date = null;
	private String user = null;
	private String machine = null;
	private boolean checkedout = false;
	private String comment = null;
	private String branch = null;
	
	private boolean oldVersion = false;

	private SnapshotView view = null;

	private String file = null;
	private String sfile = null;
	private File version = null;
	private String revision = "0";

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
	}

	/**
	 * This method is only available to the package, because only
	 * ClearcaseEntity should be allowed to call it.
	 * 
	 * @return A new Version Entity
	 */
	static Version getEntity() {
		return new Version();
	}

	void postProcess() {
		logger.trace_function();

		String fqname = this.fqname.matches( "^\\S:\\\\.*" ) ? this.fqname : System.getProperty( "user.dir" ) + filesep + this.fqname;

		this.fqname = fqname;

		/* Find the revision number, make it zero if it is not found */
		Matcher m = p_revision.matcher( this.fqname );
		if( m.find() ) {
			// this.revision = Integer.parseInt( m.group( 1 ) );
			this.revision = m.group( 1 );
		} else {
			this.revision = "0";
		}

		String tmp = this.fqname;
		tmp = tmp.replaceFirst( "(?m)@@.*$", "" );
		tmp = tmp.replaceFirst( "(?m)^\\s+", "" );
		this.file = tmp;

		this.version = new File( tmp );
	}

	public boolean hijack() {
		if( this.version.canWrite() ) {
			return true;
		}

		return this.version.setWritable( true );
	}

	/* Getters */

	public static Version getUnextendedVersion( File file, File viewroot ) throws UCMException {
		return context.getVersionExtension( file, viewroot );
	}

	public String blame() throws UCMException {
		return this.getUser();
	}

	public String getFile() throws UCMException {
		if( !loaded ) load();

		return this.file;
	}

	/*
	public String getDate() {
		if( !loaded ) load();

		return this.date;
	}
	*/

	public String getRevision() throws UCMException {
		if( !loaded ) load();

		return this.revision;
	}

	public void load2() {
		logger.trace_function();

		HashMap<String, String> result = context.getVersion( this );

		//this.date = result.get( "date" );
        try {
			this.date = dateFormatter.parse( result.get( "date" ) );
		} catch (ParseException e) {
			this.date = null;
		}
		this.user = result.get( "user" );
		this.machine = result.get( "machine" );
		this.comment = result.get( "comment" );
		this.checkedout = result.get( "checkedout" ).length() > 0 ? true : false;
		this.kind = result.get( "kind" );
		this.branch = result.get( "branch" );

		this.loaded = true;
	}
	
	public void load() throws UCMException {
		context.loadVersion( this );
	}
	
	public static Version create( File file, boolean mkdir, SnapshotView view ) throws UCMException {

		Version.addToSourceControl( file, mkdir, view.getViewRoot() );
		
		Version version = Version.getUnextendedVersion( file, view.getViewRoot() );
		version.setView( view );
		
		return version;
	}
	
	public static void addToSourceControl( File file, boolean mkdir, File view ) throws UCMException {
		context.addToSourceControl( file, mkdir, view );
	}
	
	public void checkIn() throws UCMException {
		context.checkIn( this, false, view.getViewRoot() );
	}
	
	public void checkIn( boolean identical ) throws UCMException {
		context.checkIn( this, identical, view.getViewRoot() );
	}
	
	public void checkInIdentical() throws UCMException {
		context.checkIn( this, true, view.getViewRoot() );
	}
	
	public void checkOut() throws UCMException {
		context.checkOut( this, view.getViewRoot() );
	}
	
	public void removeVersion() throws UCMException {
		context.removeVersion( this.version, view.getViewRoot() );
	}
	
	public static void removeVersion( File file, File viewContext ) throws UCMException {
		context.removeVersion( file, viewContext );
	}
	
	public void removeName( ) throws UCMException {
		context.removeName( this.version, view.getViewRoot() );
	}
	
	public static void removeName( File file, File viewContext ) throws UCMException {
		context.removeName( file, viewContext );
	}
	
	public static void moveFile( File file, File destination, SnapshotView view ) throws UCMException {
		context.moveFile( file, destination, view.getViewRoot() );
	}
	
	public void moveFile( File destination ) throws UCMException {
		context.moveFile( version, destination, view.getViewRoot() );
	}
	
	public static void checkIn( File file, boolean identical, File view ) throws UCMException {
		context.checkIn( file, identical, view );
	}
	
	public static void checkOut( File file, File view ) throws UCMException {
		context.checkOut( file, view );
	}
	
	public static void uncheckout( File file, boolean keep, File viewContext ) throws UCMException {
		context.uncheckout( file, keep, viewContext );
	}
	
	public void uncheckout() throws UCMException {
		context.uncheckout( this.getVersion(), true, view.getViewRoot() );
	}
	
	public void uncheckout( boolean keep ) throws UCMException {
		context.uncheckout( this.getVersion(), keep, view.getViewRoot() );
	}

	public void setView( SnapshotView view ) {
		this.view = view;
	}
	
	public SnapshotView getView() {
		return view;
	}

	public void setSFile( String sfile ) {
		this.sfile = sfile;
	}

	public String getSFile() {
		return sfile;
	}
	
	public void setVersion( File file ) {
		this.version = file;
	}
	
	public File getVersion() {
		return version;
	}
	
	public void setStatus( Status status ) {
		this.status = status;
	}
	
	public Status getStatus() {
		return status;
	}
	
	public static List<File> getUncheckedIn( File viewContext ) throws UCMException {
		return context.getUnchecedInFiles( viewContext );
	}
	
	public void setOldVersion( boolean old ) {
		this.oldVersion = old;
	}
	
	public boolean isOldVersion() {
		return oldVersion;
	}
	
	/**
	 * An exception safe way to determine whether the file is under
	 * source control
	 * @param element The File to be checked
	 * @param viewContext The view context as a File path
	 * @return True if the File element is under source control
	 */
	public static boolean isUnderSourceControl( File element, File viewContext ) {
		try {
			return context.isUnderSourceControl( element, viewContext );
		} catch (UCMException e) {
			return false;
		}
	}
	
	/**
	 * An exception safe way to determine whether the file is checked out
	 * @param element The File to be checked
	 * @param viewContext The view context as a File path
	 * @return True if the File element is checked out
	 */
	public static boolean isCheckedout( File element, File viewContext ) {
		try {
			return context.isCheckedout( element, viewContext );
		} catch (UCMException e) {
			return false;
		}
	}

	public String stringify() throws UCMException {
		StringBuffer sb = new StringBuffer();
		sb.append( super.stringify() + linesep );

		sb.append( "Filename: " + this.file + linesep );
		sb.append( "Revision: " + this.revision + linesep );

		return sb.toString();
	}
}
