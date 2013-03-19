/**   
 * License Agreement for OpenSearchServer
 *
 * Copyright (C) 2011-2012 Emmanuel Keller / Jaeksoft
 * 
 * http://www.open-search-server.com
 * 
 * This file is part of OpenSearchServer.
 *
 * OpenSearchServer is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 * OpenSearchServer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with OpenSearchServer. 
 *  If not, see <http://www.gnu.org/licenses/>.
 **/

package com.jaeksoft.searchlib.web;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import com.jaeksoft.searchlib.Client;
import com.jaeksoft.searchlib.ClientCatalog;
import com.jaeksoft.searchlib.SearchLibException;
import com.jaeksoft.searchlib.filter.FilterAbstract;
import com.jaeksoft.searchlib.renderer.PagingSearchResult;
import com.jaeksoft.searchlib.renderer.Renderer;
import com.jaeksoft.searchlib.renderer.RendererLogField;
import com.jaeksoft.searchlib.renderer.RendererLogParameterEnum;
import com.jaeksoft.searchlib.request.SearchRequest;
import com.jaeksoft.searchlib.result.AbstractResultSearch;
import com.jaeksoft.searchlib.user.Role;
import com.jaeksoft.searchlib.user.User;
import com.jaeksoft.searchlib.web.controller.CommonController;

public class RendererServlet extends AbstractServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = -9214023062084084833L;

	@Override
	protected void doRequest(ServletTransaction transaction)
			throws ServletException {
		try {
			User user = transaction.getLoggedUser();
			if (user != null
					&& !user.hasRole(transaction.getIndexName(),
							Role.INDEX_QUERY))
				throw new SearchLibException("Not permitted");

			Client client = transaction.getClient();

			Renderer renderer = client.getRendererManager().get(
					transaction.getParameterString("name"));
			if (renderer == null)
				throw new SearchLibException("The renderer has not been found");
			String query = transaction.getParameterString("query");
			SearchRequest request = (SearchRequest) client
					.getNewRequest(renderer.getRequestName());
			if (request == null)
				throw new SearchLibException("No request has been found");
			setLog(renderer, request, transaction);
			request.setFromServlet(transaction);
			if (query == null)
				query = (String) transaction.getSession().getAttribute("query");
			if (query != null && query.length() > 0) {
				request.setQueryString(query);
				Integer page = transaction.getParameterInteger("page");
				if (page != null) {
					if (page < 1)
						page = 1;
					request.setStart(request.getRows() * (page - 1));
				}
				AbstractResultSearch result = (AbstractResultSearch) client
						.request(request);
				transaction.setRequestAttribute("result", result);
				if (result != null) {
					transaction.setRequestAttribute("paging",
							new PagingSearchResult(result, 10));
					transaction.setRequestAttribute(
							"rendererResult",
							ClientCatalog.getRendererResults().addResult(
									client, serverBaseURL, renderer,
									request.getQueryString()));
				}
				if (request.isFacet()) {
					SearchRequest facetRequest = new SearchRequest();
					facetRequest.copyFrom(request);
					facetRequest
							.removeFilterSource(FilterAbstract.Source.REQUEST);
					AbstractResultSearch facetResult = (AbstractResultSearch) client
							.request(facetRequest);
					transaction.setRequestAttribute("facetResult", facetResult);
				}
			}
			transaction.getSession().setAttribute("query", query);
			transaction.setRequestAttribute("query", query);
			transaction.setRequestAttribute("renderer", renderer);
			String[] hiddenParameterList = { "use", "name", "login", "key" };
			transaction.setRequestAttribute("hiddenParameterList",
					hiddenParameterList);
			StringBuffer getUrl = new StringBuffer("?query=");
			if (query != null)
				getUrl.append(URLEncoder.encode(query, "UTF-8"));
			for (String p : hiddenParameterList) {
				String v = transaction.getParameterString(p);
				if (v != null) {
					getUrl.append("&amp;");
					getUrl.append(p);
					getUrl.append('=');
					getUrl.append(URLEncoder.encode(v, "UTF-8"));
				}
			}
			transaction.setRequestAttribute("getUrl", getUrl.toString());
			StringBuffer autocompUrl = new StringBuffer("autocompletion?use=");
			autocompUrl
					.append(URLEncoder.encode(client.getIndexName(), "UTF-8"));
			if (user != null)
				user.appendApiCallParameters(autocompUrl);
			transaction.setRequestAttribute("autocompUrl",
					autocompUrl.toString());
			transaction.forward("/WEB-INF/jsp/renderer.jsp");
		} catch (Exception e) {
			throw new ServletException(e);
		}
	}

	private void setCustomLogs(RendererLogField logField,
			ServletTransaction transaction, SearchRequest request) {
		RendererLogParameterEnum rendererLogParameterEnum = logField
				.getLogParameterEnum();
		if (rendererLogParameterEnum == RendererLogParameterEnum.IP)
			if (transaction.getIPAddress() != null)
				request.addCustomLog(transaction.getIPAddress());
		if (rendererLogParameterEnum == RendererLogParameterEnum.HTTP_HEADER_FROM)
			if (transaction.getUserName() != null)
				request.addCustomLog(transaction.getUserName());
		if (rendererLogParameterEnum == RendererLogParameterEnum.HTTP_HEADER_REMOTE_USER)
			if (transaction.getUserPrincipalName() != null)
				request.addCustomLog(transaction.getUserPrincipalName());
		if (rendererLogParameterEnum == RendererLogParameterEnum.USER_SESSION_ID)
			if (transaction.getUserSessionId() != null)
				request.addCustomLog(transaction.getUserSessionId());
	}

	private void setLog(Renderer renderer, SearchRequest request,
			ServletTransaction transaction) throws SearchLibException {
		if (renderer.isLogEnabled()) {
			List<RendererLogField> rendererLogFields = renderer.getLogFields();
			if (rendererLogFields.size() > 0) {
				for (RendererLogField logField : rendererLogFields) {
					if (logField.getLogParameterEnum() != null
							&& !logField.getLogParameterEnum().name()
									.equals("")) {
						request.setLogReport(true);
						setCustomLogs(logField, transaction, request);
					}
				}
			}
		}
	}

	public static String doRenderer(String name, String query)
			throws UnsupportedEncodingException {
		StringBuffer sb = CommonController.getApiUrl("/renderer");
		if (name != null) {
			sb.append("&name=");
			sb.append(URLEncoder.encode(name, "UTF-8"));
		}
		if (query != null) {
			sb.append("&query=");
			sb.append(URLEncoder.encode(query, "UTF-8"));
		}
		return sb.toString();
	}
}