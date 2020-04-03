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
package com.redhat.devtools.intellij.quarkus.search.internal.core.java.codeaction;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.redhat.devtools.intellij.quarkus.search.core.java.codeaction.JavaCodeActionContext;
import com.redhat.devtools.intellij.quarkus.search.core.java.codeaction.JavaCodeActionExtensionPointBean;
import com.redhat.devtools.intellij.quarkus.search.core.utils.IPsiUtils;
import com.redhat.devtools.intellij.quarkus.search.internal.core.java.corrections.DiagnosticsHelper;
import com.redhat.microprofile.commons.MicroProfileJavaCodeActionParams;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Code action handler.
 * 
 * @author Angelo ZERR
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/codeaction/CodeActionHandler.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/codeaction/CodeActionHandler.java</a>
 *
 */
public class CodeActionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(CodeActionHandler.class);

	/**
	 * 
	 * @param params
	 * @param utils
	 * @return
	 */
	public List<? extends CodeAction> codeAction(MicroProfileJavaCodeActionParams params, IPsiUtils utils) {
		try {
			// Get the compilation unit
			String uri = params.getUri();
			PsiFile unit = ApplicationManager.getApplication().runReadAction((Computable<PsiFile>) () -> utils.resolveCompilationUnit(uri));
			Module module = utils.getModule(uri);
			if (unit == null) {
				return Collections.emptyList();
			}

			// Prepare the code action invocation context
			int start = DiagnosticsHelper.getStartOffset(unit, params.getRange(), utils);
			int end = DiagnosticsHelper.getEndOffset(unit, params.getRange(), utils);
			JavaCodeActionContext context = new JavaCodeActionContext(unit, start, end - start, utils, module, params);

			// Collect the available code action kinds
			List<String> codeActionKinds = new ArrayList<>();
			if (params.getContext().getOnly() != null && !params.getContext().getOnly().isEmpty()) {
				codeActionKinds.addAll(params.getContext().getOnly());
			} else {
				List<String> defaultCodeActionKinds = Arrays.asList(//
						CodeActionKind.QuickFix, //
						CodeActionKind.Refactor, //
						// JavaCodeActionKind.QUICK_ASSIST,
						CodeActionKind.Source);
				codeActionKinds.addAll(defaultCodeActionKinds);
			}

			List<CodeAction> codeActions = new ArrayList<>();
			Map<String, List<JavaCodeActionExtensionPointBean>> forDiagnostics = new HashMap<>();

			// Loop for each code action kinds to process the proper code actions
			for (String codeActionKind : codeActionKinds) {
				// Get list of code action definition for the given kind
				List<JavaCodeActionExtensionPointBean> codeActionDefinitions = JavaCodeActionExtensionPointBean.EP_NAME.extensions()
						.filter(extension -> codeActionKind.startsWith(extension.kind))
						.collect(Collectors.toList());
				if (codeActionDefinitions != null) {
					// Loop for each code action definition
					for (JavaCodeActionExtensionPointBean definition : codeActionDefinitions) {
						String forDiagnostic = definition.getTargetDiagnostic();
						if (forDiagnostic != null) {
							// The code action definition is for a given diagnostic code (QuickFix), store
							// it
							List<JavaCodeActionExtensionPointBean> definitionsFor = forDiagnostics.get(forDiagnostic);
							if (definitionsFor == null) {
								definitionsFor = new ArrayList<>();
								forDiagnostics.put(forDiagnostic, definitionsFor);
							}
							definitionsFor.add(definition);
						} else {
							// Collect the code actions
							codeActions.addAll(definition.getCodeActions(context, null));
						}
					}
				}
			}

			if (!forDiagnostics.isEmpty()) {
				// It exists code action to fix diagnostics, loop for each diagnostics
				params.getContext().getDiagnostics().forEach(diagnostic -> {
					String code = getCode(diagnostic);
					if (code != null) {
						// Try to get code action definition registered with the "for" source#code
						String key = diagnostic.getSource() + "#" + code;
						List<JavaCodeActionExtensionPointBean> definitionsFor = forDiagnostics.get(key);
						if (definitionsFor == null) {
							// Try to get code action definition registered with the "for" code
							definitionsFor = forDiagnostics.get(code);
						}
						if (definitionsFor != null) {
							for (JavaCodeActionExtensionPointBean definition : definitionsFor) {
								// Collect the code actions to fix the given diagnostic
								codeActions.addAll(definition.getCodeActions(context, diagnostic));
							}
						}
					}
				});
			}
			// sort code actions by relevant
			return codeActions;
		} catch (URISyntaxException e) {
			LOGGER.error(e.getLocalizedMessage(), e);
			return Collections.emptyList();
		}
	}

	private static String getCode(Diagnostic diagnostic) {
		Object code = null;
		try {
			Field f = diagnostic.getClass().getDeclaredField("code");
			f.setAccessible(true);
			code = f.get(diagnostic);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return getCodeString(code);
	}

	private static String getCodeString(Object codeObject) {
		if (codeObject instanceof String) {
			return ((String) codeObject);
		}
		@SuppressWarnings("unchecked")
		Either<String, Number> code = (Either<String, Number>) codeObject;
		if (code == null || code.isRight()) {
			return null;
		}
		return code.getLeft();
	}
}
