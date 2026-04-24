package org.cdpg.dx.auth.v2.lookup;

import io.vertx.core.Future;
import java.util.Optional;
import org.cdpg.dx.auth.v2.model.UserSnapshot;

/**
 * SPI for loading a user's current authorization-relevant snapshot. Used by the delegation and app
 * resolvers to cap scopes against the delegator's / owner's current role set.
 *
 * <p>An {@code Optional.empty()} success means "no such user" and typically translates into 403.
 * The {@code Future} is failed only for transport/infrastructure errors.
 */
public interface UserLookup {

  Future<Optional<UserSnapshot>> findBySub(String sub);
}