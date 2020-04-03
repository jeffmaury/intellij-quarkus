/*******************************************************************************
 * Copyright (c) 2020 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 * Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package com.redhat.devtools.intellij.quarkus.lsp4ij.operations.diagnostics;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.quarkus.lsp4ij.LSPIJUtils;
import com.redhat.devtools.intellij.quarkus.lsp4ij.LanguageServerWrapper;
import com.redhat.devtools.intellij.quarkus.lsp4ij.LanguageServiceAccessor;
import com.redhat.devtools.intellij.quarkus.lsp4ij.operations.quickfix.LSPCodeActionQuickFix;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class LSPLocalInspectionTool extends LocalInspectionTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(LSPLocalInspectionTool.class);

    @Nls
    @NotNull
    @Override
    public String getDisplayName() {
        return "LSP";
    }

    @Nls
    @NotNull
    @Override
    public String getGroupDisplayName() {
        return "LSP";
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Nullable
    @Override
    public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile != null) {
            Editor editor = LSPIJUtils.editorForFile(virtualFile);
            if (editor != null) {
                List<ProblemDescriptor> problemDescriptors = new ArrayList<>();
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                TextDocumentIdentifier documentId = new TextDocumentIdentifier(VfsUtilCore.virtualToIoFile(virtualFile).toURI().toString());
                try {
                    for (LanguageServerWrapper wrapper : LanguageServiceAccessor.getLSWrappers(virtualFile, capabilities -> true)) {
                        RangeHighlighter[] highlighters = LSPDiagnosticsToMarkers.getMarkers(editor, wrapper.serverDefinition.id);
                        if (highlighters != null) {
                            boolean supportsCodeAction = isSupportsCodeAction(wrapper);
                            for (RangeHighlighter highlighter : highlighters) {
                                PsiElement element = new LSPPSiElement(editor.getProject(), file, highlighter.getStartOffset(), highlighter.getEndOffset(), editor.getDocument().getText(new TextRange(highlighter.getStartOffset(), highlighter.getEndOffset())));
                                ProblemHighlightType highlightType = getHighlighType(((Diagnostic) highlighter.getErrorStripeTooltip()).getSeverity());
                                if (supportsCodeAction) {
                                    CodeActionParams codeActionParams = new CodeActionParams();
                                    codeActionParams.setContext(new CodeActionContext(Collections.singletonList((Diagnostic) highlighter.getErrorStripeTooltip())));
                                    codeActionParams.setTextDocument(documentId);
                                    codeActionParams.setRange(new Range(LSPIJUtils.toPosition(highlighter.getStartOffset(), editor.getDocument()), LSPIJUtils.toPosition(highlighter.getEndOffset(), editor.getDocument())));
                                    futures.add(wrapper.getInitializedServer().thenComposeAsync(server -> server.getTextDocumentService().codeAction(codeActionParams)).thenAcceptAsync(actions -> actions.stream().filter(Objects::nonNull).forEach(action -> problemDescriptors.add(manager.createProblemDescriptor(element, ((Diagnostic) highlighter.getErrorStripeTooltip()).getMessage(), true, highlightType, isOnTheFly, new LSPCodeActionQuickFix(action, documentId))))));
                                } else {
                                    problemDescriptors.add(manager.createProblemDescriptor(element, ((Diagnostic) highlighter.getErrorStripeTooltip()).getMessage(), true, highlightType, isOnTheFly));
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn(e.getLocalizedMessage(), e);
                }
                try {
                    CompletableFuture
                            .allOf(futures.toArray(new CompletableFuture[futures.size()])).get(10, TimeUnit.SECONDS);
                    return problemDescriptors.toArray(new ProblemDescriptor[problemDescriptors.size()]);
                } catch (InterruptedException e) {
                    LOGGER.error(e.getLocalizedMessage(), e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException e) {
                    LOGGER.error(e.getLocalizedMessage(), e);
                }
            }

        }
        return super.checkFile(file, manager, isOnTheFly);
    }

    private boolean isSupportsCodeAction(LanguageServerWrapper wrapper) {
        Either<Boolean, CodeActionOptions> codeActionProvider = wrapper.getServerCapabilities().getCodeActionProvider();
        return codeActionProvider == null || (codeActionProvider.isLeft() && Boolean.TRUE.equals(codeActionProvider.getLeft())) || codeActionProvider.isRight();
    }

    private ProblemHighlightType getHighlighType(DiagnosticSeverity severity) {
        switch (severity) {
            case Error:
                return ProblemHighlightType.ERROR;
            case Hint:
            case Information:
                return ProblemHighlightType.INFORMATION;
            case Warning:
                return ProblemHighlightType.WARNING;
        }
        return ProblemHighlightType.INFORMATION;
    }
}
