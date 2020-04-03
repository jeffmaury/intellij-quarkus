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
package com.redhat.devtools.intellij.quarkus.search.core.java.codeaction;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Diagnostic;

import java.util.Collection;
import java.util.Collections;

public class JavaCodeActionExtensionPointBean extends AbstractExtensionPointBean {
    public static final ExtensionPointName<JavaCodeActionExtensionPointBean> EP_NAME = ExtensionPointName.create("com.redhat.devtools.intellij.quarkus.javaCodeActionParticipant");

    @Attribute("kind")
    public String kind;

    @Attribute("targetDiagnostic")
    public String targetDiagnostic;

    @Attribute("implementationClass")
    public String implementationClass;

    public String getTargetDiagnostic() {
        return targetDiagnostic;
    }

    private IJavaCodeActionParticipant participant;

    private IJavaCodeActionParticipant getParticipant() {
        if (participant == null) {
            try {
                participant = instantiate(implementationClass, ApplicationManager.getApplication().getPicoContainer());
            } catch (ClassNotFoundException e) {}
        }
        return participant;
    }

    public Collection<? extends CodeAction> getCodeActions(JavaCodeActionContext context, Diagnostic diagnostic) {
        IJavaCodeActionParticipant participant = getParticipant();
        return participant!=null?participant.getCodeActions(context, diagnostic): Collections.emptyList();
    }
}
