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

import java.util.List;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.redhat.devtools.intellij.quarkus.search.core.java.diagnostics.IJavaDiagnosticsParticipant;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

/**
 * Java codeAction participants API.
 * 
 * @author Angelo ZERR
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/java/codeaction/JavaCodeActionContext.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/core/java/codeaction/JavaCodeActionContext.java</a>
 *
 */
public interface IJavaCodeActionParticipant {
	/**
	 * Return the code action list for a given compilation unit and null otherwise.
	 * 
	 * @param context    the java code action context.
	 * @param diagnostic the diagnostic which must be fixed and null otherwise.
	 * @return the code action list for a given compilation unit and null otherwise.
	 */
	List<? extends CodeAction> getCodeActions(JavaCodeActionContext context, Diagnostic diagnostic);
}
