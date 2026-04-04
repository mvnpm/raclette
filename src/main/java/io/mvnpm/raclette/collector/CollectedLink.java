package io.mvnpm.raclette.collector;

import io.mvnpm.raclette.types.BaseInfo;
import io.mvnpm.raclette.types.RawUri;

/**
 * An unresolved link paired with its resolution context.
 * The caller resolves via {@code baseInfo.parseUrlText(rawUri.text())}.
 */
public record CollectedLink(RawUri rawUri, BaseInfo baseInfo) {
}
