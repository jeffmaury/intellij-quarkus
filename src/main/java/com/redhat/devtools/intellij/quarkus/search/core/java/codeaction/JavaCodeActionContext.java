/*******************************************************************************
* Copyright (c) 2020 Red Hat Inc. and others.
* All rights reserved. This program and the accompanying materials
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v20.html
*
* SPDX-License-Identifier: EPL-2.0
*
* Contributors:
*     Red Hat Inc. - initial API and implementation
*******************************************************************************/
package com.redhat.devtools.intellij.quarkus.search.core.java.codeaction;

import com.intellij.ide.scratch.ScratchRootType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.temp.TempFileSystem;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.redhat.devtools.intellij.quarkus.search.core.java.AbstractJavaContext;
import com.redhat.devtools.intellij.quarkus.search.core.utils.IPsiUtils;
import com.redhat.devtools.intellij.quarkus.search.internal.core.java.ChangeUtil;
import com.redhat.microprofile.commons.MicroProfileJavaCodeActionParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.WorkspaceEdit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Java codeAction context for a given compilation unit.
 * 
 * @author Angelo ZERR
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/java/codeaction/JavaCodeActionContext.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/java/codeaction/JavaCodeActionContext.java</a>
 *
 */
public class JavaCodeActionContext extends AbstractJavaContext implements IInvocationContext {

	private final int selectionOffset;
	private final int selectionLength;

	private final MicroProfileJavaCodeActionParams params;
	private PsiFile fASTRoot;

	public JavaCodeActionContext(PsiFile typeRoot, int selectionOffset, int selectionLength, IPsiUtils utils,
								 Module module, MicroProfileJavaCodeActionParams params) {
		super(params.getUri(), typeRoot, utils, module);
		this.selectionOffset = selectionOffset;
		this.selectionLength = selectionLength;
		this.params = params;
	}

	public MicroProfileJavaCodeActionParams getParams() {
		return params;
	}

	@Override
	public PsiFile getCompilationUnit() {
		return getTypeRoot();
	}

	/**
	 * Returns the length.
	 *
	 * @return int
	 */
	@Override
	public int getSelectionLength() {
		return selectionLength;
	}

	/**
	 * Returns the offset.
	 *
	 * @return int
	 */
	@Override
	public int getSelectionOffset() {
		return selectionOffset;
	}

	@Override
	public PsiFile getASTRoot() {
		if (fASTRoot == null) {
			try {
				String content = VfsUtil.loadText(getCompilationUnit().getVirtualFile());
				//VirtualFile tempFile = ScratchRootType.getInstance().createScratchFile(getJavaProject().getProject(), getCompilationUnit().getName(), JavaLanguage.INSTANCE, content);
				Path path = Files.createTempFile("", ".java");
				Files.write(path, Collections.singleton(content));
				VirtualFile tempFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile());
				fASTRoot = PsiManager.getInstance(getJavaProject().getProject()).findFile(tempFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return fASTRoot;
	}

	@Override
	public PsiElement getCoveringNode() {
		//TODO: check parent node search
		return getASTRoot().findElementAt(selectionOffset);
	}

	@Override
	public PsiElement getCoveredNode() {
		return getASTRoot().findElementAt(selectionOffset);
	}

	public CodeAction convertToCodeAction(String name, PsiClass unit, List<DocumentEvent> events, Diagnostic... diagnostics) {
		WorkspaceEdit edit = ChangeUtil.convertToWorkspaceEdit(unit, events, getUri(), getUtils(),
				params.isResourceOperationSupported());
		if (!ChangeUtil.hasChanges(edit)) {
			return null;
		}
		CodeAction codeAction = new CodeAction(name);
		//codeAction.setRelevance(proposal.getRelevance());
		codeAction.setKind("quickfix");
		codeAction.setEdit(edit);
		codeAction.setDiagnostics(Arrays.asList(diagnostics));
		return codeAction;
	}

}
