package org.platformlayer.client.cli.output;

import java.io.IOException;
import java.util.LinkedHashMap;

import org.platformlayer.core.model.ServiceInfo;

import com.fathomdb.cli.CliContext;
import com.fathomdb.cli.formatter.SimpleFormatter;
import com.fathomdb.cli.output.OutputSink;
import com.google.common.collect.Maps;

public class ServiceInfoFormatter extends SimpleFormatter<ServiceInfo> {

	public ServiceInfoFormatter() {
		super(ServiceInfo.class);
	}

	@Override
	public void visit(CliContext context, ServiceInfo o, OutputSink sink) throws IOException {
		LinkedHashMap<String, Object> values = Maps.newLinkedHashMap();

		values.put("serviceType", o.getServiceType());
		values.put("namespace", o.getNamespace());
		values.put("description", o.getDescription());
		values.put("allTypes", o.getItemTypes());

		sink.outputRow(values);
	}

}
