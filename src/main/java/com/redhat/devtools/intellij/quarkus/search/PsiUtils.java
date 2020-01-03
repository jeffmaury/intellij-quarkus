/*******************************************************************************
 * Copyright (c) 2019 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.quarkus.search;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.redhat.microprofile.commons.ClasspathKind;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * {@link IPsiUtils} implementation.
 *
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/ls/JDTUtilsLSImpl.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/ls/JDTUtilsLSImpl.java</a>
 */
public class PsiUtils implements IPsiUtils {
    private static final IPsiUtils INSTANCE = new PsiUtils();

    public static IPsiUtils getInstance() {
        return INSTANCE;
    }

    private PsiUtils() {
    }

    @Override
    public VirtualFile findFile(String uri) throws URISyntaxException {
        return LocalFileSystem.getInstance().findFileByIoFile(Paths.get(new URI(uri)).toFile());
    }

    @Override
    public PsiClass findClass(Module module, String className) {
        JavaPsiFacade facade = JavaPsiFacade.getInstance(module.getProject());
        return facade.findClass(className, GlobalSearchScope.allScope(module.getProject()));
    }

    @Override
    public void discoverSource(PsiFile classFile) {
        //TODO
    }

    @Override
    public Location toLocation(PsiMember psiMember) {
        PsiElement sourceElement = psiMember.getSourceElement();
        if (sourceElement != null) {
            PsiFile file = sourceElement.getContainingFile();
            Location location = new Location();
            location.setUri(file.getVirtualFile().getUrl());
            Document document = PsiDocumentManager.getInstance(psiMember.getProject()).getDocument(file);
            TextRange range = sourceElement.getTextRange();
            int startLine = document.getLineNumber(range.getStartOffset());
            int startLineOffset = document.getLineStartOffset(startLine);
            int endLine = document.getLineNumber(range.getEndOffset());
            int endLineOffset = document.getLineStartOffset(endLine);
            location.setRange(new Range(new Position(startLine + 1, range.getStartOffset() - startLineOffset + 1), new Position(endLine + 1, range.getEndOffset() - endLineOffset + 1)));
            return location;

        }
        return null;
    }

    public static ClasspathKind getClasspathKind(VirtualFile file, Module module) {
        return ModuleRootManager.getInstance(module).getFileIndex().isInTestSourceContent(file)?ClasspathKind.TEST:ClasspathKind.SRC;
    }

    public static String getProjectURI(Module module) {
        return module.getModuleFilePath();
    }
}
