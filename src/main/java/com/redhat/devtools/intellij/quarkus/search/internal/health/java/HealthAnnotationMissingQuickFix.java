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
package com.redhat.devtools.intellij.quarkus.search.internal.health.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.redhat.devtools.intellij.quarkus.search.core.java.codeaction.IJavaCodeActionParticipant;
import com.redhat.devtools.intellij.quarkus.search.core.java.codeaction.JavaCodeActionContext;
import com.redhat.devtools.intellij.quarkus.search.internal.health.MicroProfileHealthConstants;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * QuickFix for fixing
 * {@link MicroProfileHealthErrorCode#HealthAnnotationMissing} error by
 * providing several code actions:
 * 
 * <ul>
 * <li>Insert @Liveness annotation and the proper import.</li>
 * <li>Insert @Readiness annotation and the proper import.</li>
 * <li>Insert @Health annotation and the proper import.</li>
 * </ul>
 * 
 * @author Angelo ZERR
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/health/java/HealthAnnotationMissingQuickFix.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/health/java/HealthAnnotationMissingQuickFix.java</a>
 *
 */
public class HealthAnnotationMissingQuickFix implements IJavaCodeActionParticipant {

	@Override
	public List<? extends CodeAction> getCodeActions(JavaCodeActionContext context, Diagnostic diagnostic) {
		PsiElement node = context.getCoveredNode();
		PsiClass parentType = PsiTreeUtil.getParentOfType(node, PsiClass.class);
		if (parentType != null) {
			List<CodeAction> codeActions = new ArrayList<>();
			insertAnnotation(diagnostic, context, parentType, MicroProfileHealthConstants.HEALTH_ANNOTATION,
					codeActions);
			insertAnnotation(diagnostic, context, parentType, MicroProfileHealthConstants.LIVENESS_ANNOTATION,
					codeActions);
			insertAnnotation(diagnostic, context, parentType, MicroProfileHealthConstants.READINESS_ANNOTATION,
					codeActions);
			return codeActions;
		}
		return null;
	}

	private static class AccumulatingDocumentListener implements DocumentListener {
		private List<DocumentEvent> events = new ArrayList<>();

		@Override
		public void documentChanged(@NotNull DocumentEvent event) {
			events.add(event);
		}

		public List<DocumentEvent> getEvents() {
			return events;
		}
	}

	private static void insertAnnotation(Diagnostic diagnostic, JavaCodeActionContext context, PsiClass parentType,
										 String annotation, List<CodeAction> codeActions) {
		// Insert the annotation and the proper import by using JDT Core Manipulation
		// API
		String name = annotation.substring(annotation.lastIndexOf('.') + 1, annotation.length());
		JavaPsiFacade facade = JavaPsiFacade.getInstance(parentType.getProject());
		PsiClass annotationClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> facade.findClass(annotation, GlobalSearchScope.allScope(parentType.getProject())));
		Document document = ApplicationManager.getApplication().runReadAction((Computable<Document>)() -> PsiDocumentManager.getInstance(parentType.getProject()).getDocument(parentType.getContainingFile()));
		if (annotationClass != null && document != null) {
			AccumulatingDocumentListener listener = new AccumulatingDocumentListener();
			document.addDocumentListener(listener);
			WriteCommandAction.runWriteCommandAction(parentType.getProject(), () -> {
				((PsiJavaFile)parentType.getContainingFile()).getImportList().add(facade.getElementFactory().createImportStatement(annotationClass));
				//parentType.add(facade.getParserFacade().createAnnotationFromText("@" + name, parentType));
				parentType.getModifierList().addAnnotation(name);
			});
			document.removeDocumentListener(listener);
			System.out.println("Document=" + document.getText());
			// Convert the proposal to LSP4J CodeAction
			CodeAction codeAction = context.convertToCodeAction("Insert @" + name, parentType, listener.getEvents(), diagnostic);
			codeActions.add(codeAction);
		}
	}
}
