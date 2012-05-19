package org.platformlayer.client.cli.commands;

import java.io.PrintWriter;
import java.util.List;

import org.codehaus.jettison.json.JSONException;
import org.kohsuke.args4j.Argument;
import org.platformlayer.Format;
import org.platformlayer.PlatformLayerClient;
import org.platformlayer.PlatformLayerClientException;
import org.platformlayer.UntypedItem;
import org.platformlayer.client.cli.model.ItemPath;
import org.platformlayer.core.model.PlatformLayerKey;
import org.platformlayer.xml.XmlHelper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.fathomdb.cli.CliException;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

public class SetProperty extends PlatformLayerCommandRunnerBase {
	@Argument(index = 0, required = true, metaVar = "path")
	public ItemPath path;

	@Argument(index = 1, required = true, metaVar = "key")
	public String key;
	@Argument(index = 2, required = true, metaVar = "value")
	public String value;

	public SetProperty() {
		super("set", "property");
	}

	@Override
	public Object runCommand() throws PlatformLayerClientException, JSONException {
		PlatformLayerClient client = getPlatformLayerClient();

		PlatformLayerKey resolved = path.resolve(getContext());

		UntypedItem item = client.getItemUntyped(resolved);

		Element element = item.getRoot();
		List<String> tokens = Lists.newArrayList(Splitter.on(".").split(key));
		for (int i = 0; i < tokens.size(); i++) {
			String token = tokens.get(i);
			Node child = XmlHelper.getChildElement(element, token);
			if (child == null) {
				if (i == tokens.size() - 1) {
					child = element.getOwnerDocument().createElement(token);
					element.appendChild(child);
				} else {
					throw new CliException("Cannot find element: " + token);
				}
			}
			element = (Element) child;
		}

		element.setTextContent(value);

		String xml = item.serialize();
		// System.out.println(xml);
		UntypedItem updated = client.putItem(resolved, xml, Format.XML);

		return updated;
	}

	@Override
	public void formatRaw(Object o, PrintWriter writer) {
		UntypedItem item = (UntypedItem) o;
		writer.println(item.getPlatformLayerKey());
	}

}
