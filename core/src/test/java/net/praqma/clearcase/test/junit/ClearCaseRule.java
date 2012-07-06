package net.praqma.clearcase.test.junit;

import java.io.File;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import net.praqma.clearcase.Environment;
import net.praqma.clearcase.exceptions.ClearCaseException;
import net.praqma.clearcase.exceptions.CleartoolException;
import net.praqma.clearcase.test.annotations.ClearCaseFullVobName;
import net.praqma.clearcase.test.annotations.ClearCaseLess;
import net.praqma.clearcase.test.annotations.ClearCaseUniqueVobName;
import net.praqma.clearcase.ucm.entities.Activity;
import net.praqma.clearcase.ucm.entities.Stream;
import net.praqma.clearcase.ucm.view.UCMView;
import net.praqma.clearcase.util.ExceptionUtils;
import net.praqma.clearcase.util.SetupUtils;

public class ClearCaseRule extends Environment implements TestRule {

	protected Description testDescription;
	
	protected String name;
	protected String vobName;
	protected String vobName1;
	protected File setupFile;
	
	public ClearCaseRule( String name ) {
		this.name = name;
		this.vobName1 = name + "_" + Environment.getUniqueTimestamp();
	}
	
	public ClearCaseRule( String name, String setupFile ) {
		this.name = name;
		this.vobName1 = name + "_" + Environment.getUniqueTimestamp();
		this.setupFile = new File( Environment.class.getClassLoader().getResource( setupFile ).getFile() );
	}
	
	public String getVobName() {
		return vobName;
	}

	protected void before( String name ) throws Exception {
		variables.put( "vobname", name );
		variables.put( "pvobname", name + "_PVOB" );
		
		this.vobName = name;
		
		if( setupFile != null ) {
			bootStrap( setupFile );
		} else {
			bootStrap();
		}
	}

	protected void after() {
		if( pvob != null ) {
			try {
				SetupUtils.tearDown( pvob );
			} catch( CleartoolException e ) {
				ExceptionUtils.print( e, System.out, true );
			}
		} else {
			/* Not possible to tear down */
		}
	}

	@Override
	public Statement apply( final Statement base, final Description description ) {
		
		/* ClearCase less test, just return base */
		if( description.getAnnotation( ClearCaseLess.class ) != null ) {
			return base;
		}
		
		String thisVobName = vobName1;
		
		/* Test for ClearCase annotations */
		
		/* Set an explicit vob name */
		if( description.getAnnotation( ClearCaseFullVobName.class ) != null ) {
			ClearCaseFullVobName d = description.getAnnotation( ClearCaseFullVobName.class );
			if( d.name().length() > 0 ) {
				thisVobName = d.name();
			}
		}
		
		/* Set a unique vob name */
		if( description.getAnnotation( ClearCaseUniqueVobName.class ) != null ) {
			ClearCaseUniqueVobName d = description.getAnnotation( ClearCaseUniqueVobName.class );
			if( d.name().length() > 0 ) {
				thisVobName = vobName1 + "-" + d.name();
			}
		}
		
		final String theVobName = thisVobName;
		
		return new Statement() {
						
			@Override
			public void evaluate() throws Throwable {
				testDescription = description;
				Thread t = Thread.currentThread();
				String o = t.getName();
				t.setName( "Executing " + testDescription.getDisplayName() );
				System.out.println( " ===== Setting up ClearCase =====" );
				before( theVobName );
				try {
					System.out.println( " ===== Running test: " + testDescription.getDisplayName() + " [" + testDescription.getMethodName() + "] =====" );
					base.evaluate();
				} finally {
					System.out.println( " ===== Tearing down ClearCase =====" );
					after();
					testDescription = null;
					t.setName( o );
				}
			}
		};
	}

	
	
	public File getDynamicPath( String viewtag ) {
		return new File( context.mvfs + "/" + viewtag + "/" + getVobName() );
	}
	
	public File setDynamicActivity( Stream stream, String viewtag, String name ) throws ClearCaseException {
		System.out.println( "VIEW: " + context.views.get( viewtag ) );
		File path = new File( context.mvfs + "/" + viewtag + "/" + getVobName() );
				
		System.out.println( "PATH: " + path );
		
		Activity activity = Activity.create( name, stream, getPVob(), true, "activity for " + name, null, path );
		UCMView.setActivity( activity, path, null, null );
		
		return path;
	}
	
}