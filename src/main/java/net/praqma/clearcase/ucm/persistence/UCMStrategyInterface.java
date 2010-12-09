package net.praqma.clearcase.ucm.persistence;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.praqma.clearcase.ucm.entities.*;
import net.praqma.clearcase.ucm.UCMException;
import net.praqma.utils.Tuple;

interface UCMStrategyInterface
{
	public static final String delim = "::";
	
	/* General command line stuff */
	public void ChangeDirectoryToView( String path );
	public void CheckViewContext( File dir );
	
	/* Project */
	String GetProjectFromStream( String stream );
	List<String> GetModifiableComponents( String project );
	public String LoadProject( String project );
	
	/* Activity */
	public String LoadActivity( String activity );
	
	/* Baseline */
	public String LoadBaseline( String baseline );
	public List<String> GetBaselineDiff( File dir, String baseline, String other, boolean nmerge, String pvob );
	public void SetPromotionLevel( String baseline, String plevel );
	
	public String GetBaselineActivities( String baseline );
	
	/* Component */
	public List<String> GetBaselines( String component, String stream, String plevel );
	public String GetRootDir( String component );
	
	/* Stream */
	public void RecommendBaseline( String stream, String baseline ) throws UCMException;
	public String GetRecommendedBaselines( String stream );
	public String GetStreamFromView( String viewtag );
	public void CreateStream( String pstream, String nstream, boolean readonly, String baseline );
	public boolean StreamExists( String fqname );
	public boolean RebaseStream( String viewtag, String stream, String baseline, boolean complete );
	public boolean IsRebaseInProgress( String stream );
	public void CancelRebase( String stream );
	public List<String> GetLatestBaselines( String stream );
	public void Generate( String stream );
	
	/* Version */
	public String GetVersion( String version, String separator );
	
	/* Tag */
	public List<Tuple<String, String>> GetTags( String fqname );
	public String GetTag( String fqname );
	public String NewTag( UCMEntity entity, String cgi );
	public void DeleteTag( String fqname );
	public void DeleteTagsWithID( String tagType, String tagID, String entity );
	
	public String PutTag( String fqname, String keyval, UCMEntity entity );
	
	/* Snapshot Views */
	public void MakeSnapshotView( String stream, File viewroot, String viewtag );
	public File GetCurrentViewRoot( File viewroot );
	public String ViewrootIsValid( File viewroot );
	public boolean ViewExists( String viewtag );
	public void RegenerateViewDotDat( File dir, String viewtag ) throws IOException;
	public boolean SwipeView( File viewroot, boolean excludeRoot );
	public String Cache( File viewroot, boolean overwrite, String loadrules );

		
	
	/* TEST */
	public String GetXML();
	public void SaveState();
}