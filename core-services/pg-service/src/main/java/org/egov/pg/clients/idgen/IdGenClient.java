package org.egov.pg.clients.idgen;

import java.util.Map;

public interface IdGenClient {

	String generateId(String tenantId, String templateCode, Map<String, String> variables);
}