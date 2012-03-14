package net.praqma.clearcase.ucm.utils;

import java.util.ArrayList;

import net.praqma.clearcase.ucm.entities.Activity;
import net.praqma.clearcase.ucm.entities.Baseline;
import net.praqma.clearcase.ucm.entities.UCM;
import net.praqma.clearcase.ucm.entities.Version;
import net.praqma.clearcase.ucm.view.SnapshotView;

/**
 * @deprecated
 * @author wolfgang
 *
 */
public class BaselineDiff extends ArrayList<Activity> {
	private static final long serialVersionUID = 1L;

	public BaselineDiff() {
	}

	public BaselineDiff( SnapshotView view, Baseline baseline ) {
		this.addAll( UCM.context.getBaselineDiff( view, baseline ) );
	}

	public void Print() {
		for( Activity a : this ) {
			System.out.println( "----- Activity:" );
			System.out.println( a.stringify() );
			for( Version v : a.changeset.versions ) {
				System.out.println( v.toString() );
			}
		}
	}

	public ArrayList<Version> GetUniqueFiles() {
		return null;
	}
}
