/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package com.redhat.devtools.intellij.quarkus.search.internal.core.java;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.PsiClass;
import com.redhat.devtools.intellij.quarkus.search.core.utils.IPsiUtils;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Converts an {@link DocumentEvent} to
 * {@link TextEdit}
 *
 * @author Gorkem Ercan
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/TextEditConverter.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/TextEditConverter.java</a>
 *
 */
public class TextEditConverter {

	private static final Logger LOGGER = LoggerFactory.getLogger(TextEditConverter.class);

	private final DocumentEvent source;
	protected PsiClass compilationUnit;
	protected List<TextEdit> converted;

	private final String uri;

	private final IPsiUtils utils;

	public TextEditConverter(PsiClass unit, DocumentEvent edit, String uri, IPsiUtils utils) {
		this.source = edit;
		this.converted = new ArrayList<>();
		if (unit == null) {
			throw new IllegalArgumentException("Compilation unit can not be null");
		}
		this.compilationUnit = unit;
		this.uri = uri;
		this.utils = utils;
	}

	public List<org.eclipse.lsp4j.TextEdit> convert() {
		if (this.converted == null) {
			this.converted = Collections.singletonList(toTextEdit());
		}
		return converted;
	}

	public TextDocumentEdit convertToTextDocumentEdit(int version) {
		VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(version);
		identifier.setUri(uri);
		return new TextDocumentEdit(identifier, this.convert());
	}

	private TextEdit toTextEdit() {
			TextEdit te = new TextEdit();
			te.setNewText(source.getNewFragment().toString());
			te.setRange(utils.toRange(compilationUnit, source.getOffset(), source.getNewLength()));
			return te;
	}
}
