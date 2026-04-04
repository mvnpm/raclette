package io.mvnpm.raclette.types;

/**
 * Thrown when a relative link cannot be resolved against the available base information.
 * Mirrors lychee's ErrorKind::RootRelativeLinkWithoutRoot and ParseError::RelativeUrlWithoutBase.
 */
public class LinkResolutionException extends RuntimeException {

    public enum Kind {
        /**
         * A root-relative link (e.g. "/docs/page") was found but no root directory
         * is configured. Without a root, there is no way to resolve where "/" points to.
         * Lychee: ErrorKind::RootRelativeLinkWithoutRoot
         */
        ROOT_RELATIVE_WITHOUT_ROOT,

        /**
         * A relative link (e.g. "page.html", "../other") was found but no base URL
         * or file path is available to resolve it against (e.g. input from stdin).
         * Lychee: ParseError::RelativeUrlWithoutBase
         */
        RELATIVE_WITHOUT_BASE
    }

    private final Kind kind;
    private final String link;

    public LinkResolutionException(Kind kind, String link) {
        super(switch (kind) {
            case ROOT_RELATIVE_WITHOUT_ROOT ->
                "Root-relative link without root directory: " + link;
            case RELATIVE_WITHOUT_BASE ->
                "Relative link without base: " + link;
        });
        this.kind = kind;
        this.link = link;
    }

    public Kind kind() {
        return kind;
    }

    public String link() {
        return link;
    }
}
