package io.mvnpm.raclette.checker;

import java.util.Set;

/**
 * Validates #fragment references against HTML content.
 * Handles GitHub's user-content- prefix convention.
 */
public class FragmentChecker {

    /**
     * Check if a fragment exists in the given set of known fragments.
     *
     * @param fragment the fragment to check (without #)
     * @param knownFragments set of fragment identifiers found in the document
     * @return true if the fragment is found
     */
    public boolean check(String fragment, Set<String> knownFragments) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
