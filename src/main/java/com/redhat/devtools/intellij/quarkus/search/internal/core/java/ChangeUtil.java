package com.redhat.devtools.intellij.quarkus.search.internal.core.java;

import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.psi.PsiClass;
import com.redhat.devtools.intellij.quarkus.search.core.utils.IPsiUtils;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ResourceOperation;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @see <a href="https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/ChangeUtil.java">https://github.com/redhat-developer/quarkus-ls/blob/master/microprofile.jdt/com.redhat.microprofile.jdt.core/src/main/java/com/redhat/microprofile/jdt/internal/core/java/ChangeUtil.java</a>
 */
public class ChangeUtil {

	private static final Range ZERO_RANGE = new Range(new Position(), new Position());

	/**
	 * Converts Change to WorkspaceEdit for further consumption.
	 *
	 * @param events {@link DocumentEvent} to convert
	 * @return {@link WorkspaceEdit} converted from the change
	 */
	public static WorkspaceEdit convertToWorkspaceEdit(PsiClass unit, List<DocumentEvent> events, String uri, IPsiUtils utils,
													   boolean resourceOperationSupported) {
		WorkspaceEdit edit = new WorkspaceEdit();
		events.forEach(event -> convertSingleChange(unit, event, edit, uri, utils, resourceOperationSupported));
		return edit;
	}

	private static void convertSingleChange(PsiClass unit, DocumentEvent change, WorkspaceEdit edit, String uri, IPsiUtils utils,
			boolean resourceOperationSupported) {
			convertTextChange(unit, change, edit, uri, utils, resourceOperationSupported);
	}

	private static void convertTextChange(PsiClass unit, DocumentEvent textChange, WorkspaceEdit rootEdit, String uri, IPsiUtils utils,
			boolean resourceOperationSupported) {
		convertTextEdit(rootEdit, unit, textChange, uri, utils, resourceOperationSupported);
	}

	private static void convertTextEdit(WorkspaceEdit root, PsiClass unit, DocumentEvent edit, String uri,
			IPsiUtils utils, boolean resourceOperationSupported) {
		if (edit == null) {
			return;
		}

		TextEditConverter converter = new TextEditConverter(unit, edit, uri, utils);
		if (resourceOperationSupported) {
			List<Either<TextDocumentEdit, ResourceOperation>> changes = root.getDocumentChanges();
			if (changes == null) {
				changes = new ArrayList<>();
				root.setDocumentChanges(changes);
			}

			VersionedTextDocumentIdentifier identifier = new VersionedTextDocumentIdentifier(uri, 0);
			TextDocumentEdit documentEdit = new TextDocumentEdit(identifier, converter.convert());
			changes.add(Either.forLeft(documentEdit));
		} else {

			Map<String, List<org.eclipse.lsp4j.TextEdit>> changes = root.getChanges();
			if (changes.containsKey(uri)) {
				changes.get(uri).addAll(converter.convert());
			} else {
				changes.put(uri, converter.convert());
			}
		}
	}

	/**
	 * @return <code>true</code> if a {@link WorkspaceEdit} contains any actual
	 *         changes, <code>false</code> otherwise.
	 */
	public static boolean hasChanges(WorkspaceEdit edit) {
		if (edit == null) {
			return false;
		}
		if (edit.getDocumentChanges() != null && !edit.getDocumentChanges().isEmpty()) {
			return true;
		}
		boolean hasChanges = false;
		// @formatter:off
		if ((edit.getChanges() != null && !edit.getChanges().isEmpty())) {
			hasChanges = edit.getChanges().values().stream()
					.filter(changes -> changes != null && !changes.isEmpty() && hasChanges(changes)).findFirst()
					.isPresent();
		}
		// @formatter:on
		return hasChanges;
	}

	/**
	 * @return <code>true</code> if a list of {@link org.eclipse.lsp4j.TextEdit}
	 *         contains any actual changes, <code>false</code> otherwise.
	 */
	public static boolean hasChanges(List<org.eclipse.lsp4j.TextEdit> edits) {
		if (edits == null) {
			return false;
		}
		// @formatter:off
		return edits.stream().filter(edit -> (!edit.getRange().equals(ZERO_RANGE) || !"".equals(edit.getNewText())))
				.findFirst().isPresent();
		// @formatter:on
	}
}
