/*******************************************************************************
 * Copyright (c) 2009 Laurent Petit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors: 
 *    Laurent PETIT - initial API and implementation
 *******************************************************************************/
package ccw.editors.clojure;

import org.apache.commons.io.FilenameUtils;
import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jface.action.Action;
import org.eclipse.ui.ide.FileStoreEditorInput;

import ccw.CCWPlugin;
import ccw.ClojureCore;
import ccw.ClojureProject;
import ccw.TraceOptions;
import ccw.launching.ClojureLaunchShortcut;
import ccw.repl.Actions;
import ccw.repl.REPLView;
import ccw.util.ClojureInvoker;
import ccw.util.DisplayUtil;

public class LoadFileAction extends Action {

	private ClojureInvoker nreplHelpers = ClojureInvoker.newInvoker(CCWPlugin.getDefault(), "clojure.tools.nrepl.helpers");
	
	public final static String ID = "LoadFileAction"; //$NON-NLS-1$
	
	private final ClojureEditor editor;

	public LoadFileAction(ClojureEditor editor) {
		super(ClojureEditorMessages.LoadFileAction_label);
		Assert.isNotNull(editor);
		this.editor= editor;
		setEnabled(true);
	}

	public void run() {
        final IFile editorFile = (IFile) editor.getEditorInput().getAdapter(IFile.class);

        final String filePath = computeFilePath(editorFile);
        final String fileName = FilenameUtils.getName(filePath);

        if (filePath == null) {
        	editor.setStatusLineErrorMessage("Unable to create a Clojure Application for this editor's content");
        	return;
        }
        
        final String sourcePath = computeSourcePath(editorFile, filePath);

        final REPLView repl = REPLView.activeREPL.get();
        if (repl != null && !repl.isDisposed())  {
    		evaluateFileText(repl, editor.getDocument().get(), filePath, sourcePath, fileName);
        } else if (editorFile != null) {
    		CCWPlugin.getTracer().trace(TraceOptions.LAUNCHER, "No active REPL found (",
    				(repl == null) ? "active repl is null" : "active repl is disposed", 
    				"), so launching a new one");
        	new Thread(new Runnable() {
				public void run() {
		        	final IProject project = editorFile.getProject();
		        	new ClojureLaunchShortcut().launchProject(project, ILaunchManager.RUN_MODE);
		        	DisplayUtil.asyncExec(new Runnable() {
		        		public void run() {
				        	REPLView repl = CCWPlugin.getDefault().getProjectREPL(project);
				        	if (repl != null && !repl.isDisposed()) {
				        		evaluateFileText(repl, editor.getDocument().get(), filePath, sourcePath, fileName);
				        		SwitchNamespaceAction.run(repl, editor, false);
				        	} else {
				        		CCWPlugin.logError("Could not start a REPL for loading file " + filePath);
				        	}
		        		}
		        	});
				}
			}).start();
        } else {
        	editor.setStatusLineErrorMessage("Cannot start a REPL for loading " + fileName);
        }
	}

	private String computeFilePath(final IFile editorFile) {
		String filePath;
		if (editorFile != null) {
        	filePath = editorFile.getLocation().toOSString();
        } else {
        	FileStoreEditorInput fei = (FileStoreEditorInput) editor.getEditorInput();
        	IPath path = URIUtil.toPath(fei.getURI());
        	if (path != null) {
        		filePath = path.toOSString();
        	} else {
        		filePath = null;
        	}
        }
		return filePath;
	}

	private String computeSourcePath(final IFile editorFile, String filePath) {
		String sourcePath;
		if (editorFile != null) {
	        ClojureProject proj = ClojureCore.getClojureProject(editor.getProject());
	        String tempSourcePath = null;
	        if (proj != null) {
	            for (IFolder f : proj.sourceFolders()) {
	                if (f.getProjectRelativePath().isPrefixOf(editorFile.getProjectRelativePath())) {
	                	tempSourcePath = editorFile.getProjectRelativePath().makeRelativeTo(f.getProjectRelativePath()).toOSString();
	                    break;
	                }
	            }
	        }
	        sourcePath = tempSourcePath != null ? tempSourcePath : filePath;
        } else { // (filesystemFile != null)
        	// We cannot determine the source path, so let it be the full path
        	sourcePath = filePath;
        }
		return sourcePath;
	}
	
	private void evaluateFileText(REPLView repl, String text, String filePath, String sourcePath, String fileName) {
        try {
            if (repl.getAvailableOperations().contains("load-file")) {
                repl.getConnection().sendSession(repl.getSessionId(),
                        "op", "load-file", "file", text,
                        "file-path", sourcePath, "file-name", fileName);
            } else {
                String loadFileText = (String) nreplHelpers._("load-file-command", text, sourcePath, fileName);
                EvaluateTextUtil.evaluateText(repl, ";; Loading file " + filePath, false);
                EvaluateTextUtil.evaluateText(repl, loadFileText, true);
            }
            Actions.ShowActiveREPL.execute(false);
        } catch (Exception e) {
            CCWPlugin.logError("Could not load file " + filePath, e);
        }
	}
}
