// Pocket Gopher - a basic Gopher client for mobile devices
// 2010-10-31 Felix Pleșoianu <felixp7@yahoo.com>
// Some code and ideas by Nuno J. Silva <gopher://sdf-eu.org/1/users/njsg>
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
// 
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
// 
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package ro.plesoianu;

import java.util.Vector;
import java.util.Stack;
import java.io.*;
import javax.microedition.io.*;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.*;

public class PocketGopher extends MIDlet
		implements CommandListener, ItemCommandListener {
	public final String[] itemTypeLabels = new String[] {
		"0 Text file", "1 Directory", "7 Search Query",
		"h Web page", "g GIF image", "I Image"
	};
	private Form top = new Form("Pocket Gopher");
	private Command nav_cmd = new Command("Go to...", Command.SCREEN, 10);
	private Command home_cmd = new Command("Home", Command.SCREEN, 10);
	private Command stop_cmd = new Command("Stop", Command.STOP, 5);
	private Command back_cmd = new Command("Back", Command.BACK, 5);
	private Command hist_cmd = new Command("History", Command.SCREEN, 10);
	private Command exit_cmd = new Command("Exit", Command.EXIT, 10);
	private Command go_cmd = new Command("Go", Command.ITEM, 5);
	private Command dir_pgup_cmd;
	private Command dir_pgdn_cmd;
	private ImageItem imgholder = null;
		
	private Ticker loading_notification = new Ticker("Loading...");
	private Alert item_fail = new Alert(
		"Unsupported item type",
		"Pocket Gopher does not handle binary files.",
		null, AlertType.INFO);
	private Alert net_fail = new Alert(
		"Failure",
		"Can't fetch the requested item",
		null, AlertType.ERROR);

	private Form bottom = null;
	private Command btm_close_cmd;
	private Command txt_pgup_cmd;
	private Command txt_pgdn_cmd;
		
	private Form navform = null;
	private Command goto_cmd;
	private Command nogo_cmd;
	private TextField url_fld;
	private TextField host_fld;
	private TextField port_fld;
	private ChoiceGroup type_fld;
	private TextField selector_fld;
	private StringItem nav_clear_fld;
	private Command nav_clear_cmd;
	
	private Form queryform = null;
	private Command query_cmd;
	private Command noqry_cmd;
	private TextField query_fld;
	
	private DirectoryItem queried_item;
		
	private Vector previous_dir = new Vector();
	private Vector current_dir = new Vector();
	private int dir_page_num = 0;
	private int dir_page_size = 25; // Arbitrary value; about half a page.
	private int dir_page_count = 0;

	private Stack history = new Stack();
	private Thread loading = null;
	
	private Vector current_text;
	private int txt_page_num = 0;
	private int txt_page_size = dir_page_size;
	private int txt_page_count = 0;

	public PocketGopher() {
		top.addCommand(nav_cmd);
		top.addCommand(home_cmd);
		top.addCommand(back_cmd);
		top.addCommand(hist_cmd);
		top.addCommand(exit_cmd);
		top.setCommandListener(this);

		Display.getDisplay(this).setCurrent(top);
		goHome(); // Doing it after showing the window, for effect.
		history.push(null);
		
		dir_pgup_cmd =
			new Command("PgUp", "Page Up", Command.SCREEN, 8);
		dir_pgdn_cmd =
			new Command("PgDn", "Page Down", Command.SCREEN, 4);
	}
	
	public void startApp() { }
	public void pauseApp() { }
	public void destroyApp(boolean unconditional) {
		stopLoading();
	}
	
	public void commandAction(Command c, Displayable s) {
		if (c == exit_cmd) {
			notifyDestroyed();
		} else if (c == home_cmd) {
			history.push(null);
			previous_dir = current_dir;
			goHome();
		} else if (c == stop_cmd) {
			stopLoading();
		} else if (c == back_cmd) {
			if (history.size() > 1) {
				history.pop();
				DirectoryItem prev =
					(DirectoryItem) history.peek();
				current_dir = previous_dir;
				previous_dir = null;
				if (prev == null) {
					goHome();
				} else if (current_dir != null) {
					setUpDirPagination(current_dir, top);
					top.setTitle(
						prev.hostname
						+ " " + String.valueOf(prev.port)
						+ " " + prev.selector);
				} else {
					history.pop();
					loadItem(prev);
				}
			}
		} else if (c == hist_cmd) {
			if (bottom == null) initSecondaryView();
			bottom.deleteAll();
			addDirectoryToForm(history, bottom);
			bottom.setTitle("Session history");
			Display.getDisplay(this).setCurrent(bottom);
		} else if (c == btm_close_cmd) {
			Display.getDisplay(this).setCurrent(top);
		} else if (c == nav_cmd) {
			if (navform == null) initNavForm();
			Display.getDisplay(this).setCurrent(navform);
		} else if (c == goto_cmd) {
			Display.getDisplay(this).setCurrent(top);
			char itemType = itemTypeLabels[
				type_fld.getSelectedIndex()].charAt(0);
			DirectoryItem tmp;
			if (url_fld.getString().length() > 0) {
				tmp = Data.parseGopherURL(url_fld.getString());
			} else {
				tmp = new DirectoryItem(
					itemType, host_fld.getString(),
					selector_fld.getString(),
					host_fld.getString(),
					Integer.parseInt(port_fld.getString()));
			}
			loadItem(tmp);
		} else if (c == nogo_cmd) {
			Display.getDisplay(this).setCurrent(top);
		} else if (c == query_cmd) {
			Display.getDisplay(this).setCurrent(top);
			queried_item.selector += "\t" + query_fld.getString();
			loadDirectory(queried_item);
		} else if (c == noqry_cmd) {
			Display.getDisplay(this).setCurrent(top);
		} else if (c == txt_pgdn_cmd) {
			if (txt_page_num < txt_page_count) {
				bottom.deleteAll();
				txt_page_num++;
				paginateText(
					current_text, bottom, txt_page_num);
			}
		} else if (c == txt_pgup_cmd) {
			if (txt_page_num > 1) {
				bottom.deleteAll();
				txt_page_num--;
				paginateText(
					current_text, bottom, txt_page_num);
			}
		} else if (c == dir_pgdn_cmd) {
			if (dir_page_num < dir_page_count) {
				top.deleteAll();
				dir_page_num++;
				paginateDir(current_dir, top, dir_page_num);
			}
		} else if (c == dir_pgup_cmd) {
			if (dir_page_num > 1) {
				top.deleteAll();
				dir_page_num--;
				paginateDir(current_dir, top, dir_page_num);
			}
		}
	}
	
	public void commandAction(Command c, Item i) {
		if (c == go_cmd) {
			loadItem((DirectoryItem) i);
		} else if (c == nav_clear_cmd) {
			url_fld.setString("");
			host_fld.setString("");
			port_fld.setString("70");
			type_fld.setSelectedIndex(1, true);
			selector_fld.setString("");
		}
	}
	
	public void loadItem(DirectoryItem di) {
		stopLoading();
		switch (di.getItemType()) {
			case '0': loadTextFile(di); break;
			case '1': loadDirectory(di); break;
			case '7': loadQuery(di); break;
			case 'h': getURL(di.selector); break;
			case 'g':
			case 'I': loadImage(di); break;
			case 'i': break; // Shouldn't get here anyway.
			default:
				Display.getDisplay(this)
					.setCurrent(item_fail);
		}
	}
	
	public void goHome() {
		stopLoading();
		current_dir = Data.parseDirectory(Data.slurpInputStream(
			this.getClass().getResourceAsStream("/home.txt")));
		top.setTitle("Pocket Gopher");
		top.deleteAll();
		addDirectoryToForm(current_dir, top);
	}
	
	public void stopLoading() {
		if (loading != null) {
			loading.interrupt();
			loading = null;
		}
		top.removeCommand(stop_cmd);
		top.setTicker(null);
	}
	
	public void getURL(String url) {
		try {
			platformRequest(url);
		} catch (ConnectionNotFoundException e) {
			Display.getDisplay(this).setCurrent(net_fail);
		}
	}
	
	public void show(Displayable d) { // Change screens from other threads.
		Display.getDisplay(this).setCurrent(d);
	}
	
	public void loadTextFile(final DirectoryItem di) {
		if (bottom == null) initSecondaryView();
		loading = new Thread(new Runnable() {
			public void run() {
				top.setTicker(loading_notification);
				top.addCommand(stop_cmd);
				
				String tmp = Data.fetchText(
					di.hostname, di.port, di.selector);
				if (tmp != null) {
					current_text = Data.splitString(
						tmp.replace('\r', '\n'), '\n');
					setUpTextPagination(
						current_text, bottom);
					bottom.setTitle(
						di.hostname
						+ " " + String.valueOf(di.port)
						+ " " + di.selector);
					show(bottom);
				} else {
					show(net_fail);
				}
				top.removeCommand(stop_cmd);
				top.setTicker(null);
			}
		});
		loading.start();
	}
	
	public void loadDirectory(final DirectoryItem di) {
		loading = new Thread(new Runnable() {
			public void run() {
				top.setTicker(loading_notification);
				top.addCommand(stop_cmd);
				String content = Data.fetchText(
					di.hostname, di.port, di.selector);
				if (content != null) {
					history.push(new DirectoryItem(di));
					previous_dir = current_dir;
					current_dir =
						Data.parseDirectory(content);
					setUpDirPagination(current_dir, top);
					top.setTitle(di.hostname + " "
						+ String.valueOf(di.port) + " "
						+ di.selector);
					show(top); // Not always redundant.
				} else {
					show(net_fail);
				}
				top.removeCommand(stop_cmd);
				top.setTicker(null);
			}
		});
		loading.start();
	}
	
	public void loadImage(final DirectoryItem di) {
		if (bottom == null) initSecondaryView();
		loading = new Thread(new Runnable() {
			public void run() {
				top.setTicker(loading_notification);
				Image content = Data.fetchImage(
					di.hostname, di.port, di.selector);
				if (content != null) {
					bottom.deleteAll();
					bottom.setTitle(
						di.hostname + " "
						+ String.valueOf(di.port) + " "
						+ di.selector);
					if (imgholder == null)
						initImageHolder(content);
					else
						imgholder.setImage(content);
					bottom.append(imgholder);
					show(bottom);
				} else {
					show(net_fail);
				}
				top.setTicker(null);
			}
		});
		loading.start();
	}
	
	public void loadQuery(final DirectoryItem di) {
		queried_item = new DirectoryItem (di);
		if (queryform == null) initQueryForm();
		queryform.setTitle(
			"Query: " + di.hostname + " "
			+ String.valueOf(di.port) + " "
			+ di.selector);
		Display.getDisplay(this).setCurrent(queryform);
	}
	
	public void addDirectoryToForm(final Vector dir, Form f) {
		if (dir == null || f == null) return;
		for (int i = 0; i < dir.size(); i++) {
			addDirItemToForm((DirectoryItem) dir.elementAt(i), f);
		}
	}
	
	public void addDirItemToForm(DirectoryItem di, Form f) {
		if (di == null || f == null) return;
		if (di.getItemType() != 'i' && di.getItemType() != '3') {
			di.setDefaultCommand(go_cmd);
			di.setItemCommandListener(this);
		}
		f.append(di);
	}

	public void setUpDirPagination(final Vector dir, Form f) {
		dir_page_num = 1;
		dir_page_count = Data.numPages(dir.size(), dir_page_size);
		f.deleteAll();
		paginateDir(dir, f, 1);
		if (dir_page_count > 1) {
			f.addCommand(dir_pgup_cmd);
			f.addCommand(dir_pgdn_cmd);
		} else {
			f.removeCommand(dir_pgup_cmd);
			f.removeCommand(dir_pgdn_cmd);
		}
	}
	
	public void paginateDir(final Vector dir, Form f, int page) {
		if (page < 1)
			page = 1;
		else if (page > dir_page_count)
			page = dir_page_count;
		final int start_offset = (page - 1) * dir_page_size;
		int end_offset = start_offset + dir_page_size;
		if (end_offset > dir.size()) end_offset = dir.size();
		String pagecount = "Page " + String.valueOf(page)
			+ " of " + String.valueOf(dir_page_count);
		f.append(pagecount + "\n\n");
		for (int i = start_offset; i < end_offset; i++)
			addDirItemToForm((DirectoryItem) dir.elementAt(i), f);
		f.append("\n\n" + pagecount);
	}

	public void setUpTextPagination(final Vector text, Form f) {
		txt_page_num = 1;
		txt_page_count = Data.numPages(text.size(), txt_page_size);
		f.deleteAll();
		paginateText(text, f, 1);
		if (txt_page_count > 1) {
			f.addCommand(txt_pgup_cmd);
			f.addCommand(txt_pgdn_cmd);
		} else {
			f.removeCommand(txt_pgup_cmd);
			f.removeCommand(txt_pgdn_cmd);
		}
	}
	
	public void paginateText(final Vector text, Form f, int page) {
		if (page < 1)
			page = 1;
		else if (page > txt_page_count)
			page = txt_page_count;
		final int start_offset = (page - 1) * txt_page_size;
		int end_offset = start_offset + txt_page_size;
		if (end_offset > text.size()) end_offset = text.size();
		String pagecount = "Page " + String.valueOf(page)
			+ " of " + String.valueOf(txt_page_count);
		f.append(pagecount + "\n\n");
		for (int i = start_offset; i < end_offset; i++)
			f.append((String) text.elementAt(i) + "\n");
		f.append("\n\n" + pagecount);
	}
	
	public void initNavForm() {
		navform = new Form("Navigate to...");
		goto_cmd = new Command("Go there", Command.OK, 10);
		nogo_cmd = new Command("Cancel", Command.CANCEL, 10);
		url_fld = new TextField("Gopher URL", "", 140, TextField.URL);
		host_fld = new TextField("Hostname", "", 140, TextField.URL);
		port_fld = new TextField("Port", "70", 6, TextField.NUMERIC);
		type_fld = new ChoiceGroup(
			"Item type", Choice.POPUP, itemTypeLabels, null);
		type_fld.setSelectedIndex(1, true);
		selector_fld = new TextField(
			"Selector (optional)", "", 140, TextField.URL);
		nav_clear_fld = new StringItem(
			null, "Clear form", StringItem.BUTTON);
		nav_clear_cmd = new Command("Clear form", Command.SCREEN, 10);
		nav_clear_fld.setDefaultCommand(nav_clear_cmd);
		nav_clear_fld.setItemCommandListener(this);
		
		navform.append(url_fld);
		navform.append("Or else");
		navform.append(host_fld);
		navform.append(port_fld);
		navform.append(type_fld);
		navform.append(selector_fld);
		navform.append(nav_clear_fld);
		navform.addCommand(goto_cmd);
		navform.addCommand(nogo_cmd);
		navform.setCommandListener(this);
	}
	
	public void initSecondaryView() {
		bottom = new Form("Pocket Gopher");
		btm_close_cmd = new Command("Close", Command.BACK, 10);
		txt_pgup_cmd =
			new Command("PgUp", "Page Up", Command.SCREEN, 5);
		txt_pgdn_cmd =
			new Command("PgDn", "Page Down", Command.SCREEN, 10);
		
		bottom.addCommand(btm_close_cmd);
		bottom.setCommandListener(this);
	}
	
	public void initImageHolder(Image img) {
		int layout = ImageItem.LAYOUT_CENTER
			| ImageItem.LAYOUT_SHRINK
			| ImageItem.LAYOUT_VSHRINK;
		imgholder = new ImageItem(null, img, layout, "(image)");
	}
	
	public void initQueryForm() {
		queryform = new Form("Query server");
		query_cmd = new Command("Query", Command.OK, 10);
		noqry_cmd = new Command("Cancel", Command.CANCEL, 10);
		query_fld =
			new TextField("Your query", "", 140, TextField.ANY);
		
		queryform.append(query_fld);
		queryform.addCommand(query_cmd);
		queryform.addCommand(noqry_cmd);
		queryform.setCommandListener(this);
	}
}

class DirectoryItem extends StringItem {
	private final int layout =
		Item.LAYOUT_NEWLINE_BEFORE | Item.LAYOUT_NEWLINE_AFTER;
	private char itemType;

	public String selector;
	public String hostname;
	public int port;

	public DirectoryItem(char itemType) {
		super(null, "",
			itemType == 'i' ? Item.PLAIN : Item.HYPERLINK);
		this.itemType = itemType;
		setProperLabel();
		this.setLayout(layout);
	}

	public DirectoryItem(char itemType, String text) {
		super(null, text,
			itemType == 'i' ? Item.PLAIN : Item.HYPERLINK);
		this.itemType = itemType;
		setProperLabel();
		this.setLayout(layout);
	}
	
	public DirectoryItem(char itemType, String text,
			String selector, String hostname, int port) {
		super(null, text,
			itemType == 'i' ? Item.PLAIN : Item.HYPERLINK);
		this.itemType = itemType;
		setProperLabel();
		this.setLayout(layout);
		this.selector = selector;
		this.hostname = hostname;
		this.port = port;
	}
	
	public DirectoryItem(DirectoryItem original) {
		super(null, original.getText(),
			original.getItemType() == 'i' ?
				Item.PLAIN : Item.HYPERLINK);
		this.itemType = original.getItemType();
		setProperLabel();
		this.setLayout(layout);
		this.selector = original.selector;
		this.hostname = original.hostname;
		this.port = original.port;
	}
	
	public void setProperLabel() {
		switch (itemType) {
			case '0': setLabel("[TXT]"); break;
			case '1': setLabel("[DIR]"); break;
			case '3': setLabel("[ERR]"); break;
			case '5': setLabel("[ZIP]"); break;
			case '7': setLabel("[QRY]"); break;
			case '9': setLabel("[BIN]"); break;
			case 'g': setLabel("[GIF]"); break;
			case 'h': setLabel("[WWW]"); break;
			case 'i': setLabel(""); break;
			case 'I': setLabel("[IMG]"); break;
			default: setLabel("[???]");
		}
	}
	
	public char getItemType() { return itemType; }
}

class Data {
	public static String fetchText(String hostname, int port, String selector) {
		final String url = "socket://"
			+ hostname + ":" + String.valueOf(Math.abs(port));
		String content = null;
		SocketConnection sc = null;
		InputStream is = null;
		OutputStream os = null;
		try {
			sc = (SocketConnection) Connector.open(
				url, Connector.READ_WRITE, true);
			is  = sc.openInputStream();
			os = sc.openOutputStream();

			os.write((selector + "\r\n").getBytes());
			os.flush();
			content = slurpInputStream(is);
		} catch (IOException e) {
			System.err.println(e.toString());
		} catch (SecurityException e) {
			System.err.println(e.toString());
		} finally {
			if (is != null)
				try { is.close(); } catch (IOException e) {}
			if (os != null)
				try { os.close(); } catch (IOException e) {}
			if (sc != null)
				try { sc.close(); } catch (IOException e) {}
		}
		return content;
	}
	
	public static Image fetchImage(String hostname, int port, String selector) {
		final String url = "socket://"
			+ hostname + ":" + String.valueOf(Math.abs(port));
		Image content = null;
		SocketConnection sc = null;
		InputStream is = null;
		OutputStream os = null;
		try {
			sc = (SocketConnection) Connector.open(
				url, Connector.READ_WRITE, true);
			is  = sc.openInputStream();
			os = sc.openOutputStream();

			os.write((selector + "\r\n").getBytes());
			os.flush();
			content = Image.createImage(is);
		} catch (IOException e) {
			System.err.println(e.toString());
		} catch (SecurityException e) {
			System.err.println(e.toString());
		} finally {
			if (is != null)
				try { is.close(); } catch (IOException e) {}
			if (os != null)
				try { os.close(); } catch (IOException e) {}
			if (sc != null)
				try { sc.close(); } catch (IOException e) {}
		}
		return content;
	}
	
	public static String slurpInputStream(InputStream is) {
		if (is == null) return null;
		
		InputStreamReader isr = new InputStreamReader(is);
		final int buffer_size = 1024;
		char[] buffer = new char[buffer_size];
		StringBuffer output = new StringBuffer(buffer_size);
		
		try {
			int bytes_in = 0;
			do {
				bytes_in = isr.read(buffer);
				if (bytes_in > -1)
					output.append(buffer, 0, bytes_in);
			} while (bytes_in > -1);
		} catch (IOException e) {
			System.err.println(e.toString());
		} catch (OutOfMemoryError e) {
			System.err.println(e.toString());
		}
		
		return output.toString();
	}
	
	public static Vector parseDirectory(String text) {
		if (text == null || text.length() == 0) return new Vector();
		
		final int len = text.length();
		int mark = 0;
		int pos = 0;
		
		text = text.replace('\r', '\n');
		
		Vector output = new Vector();
		do {
			while (pos < len && text.charAt(pos) != '\n') pos++;
			output.addElement(
				parseDirectoryItem(
					text.substring(mark, pos)));
			while (pos < len && text.charAt(pos) == '\n') pos++;
			mark = pos;
		} while (pos < len);
		return output;
	}
	
	public static DirectoryItem parseDirectoryItem(String text) {
		if (text == null || text.length() == 0) return null;
		if (text.equals(".")) return null;
		
		final Vector record = splitString(text, '\t');
		final String label = (String) record.elementAt(0);
		final char itemType = label.charAt(0);
		
		DirectoryItem item =
			new DirectoryItem(itemType, label.substring(1));
		if (itemType == 'i' || itemType == '3' || record.size() < 2)
			return item;

		String selector = (String) record.elementAt(1);
		if (itemType == 'h') {
			if (selector.startsWith("URL:"))
				item.selector = selector.substring(4);
		} else {
			item.selector = selector;
		}
		
		if (record.size() > 2)
			item.hostname =	(String) record.elementAt(2);
		if (record.size() > 3) {
			try {
				item.port = Integer.parseInt(
					(String) record.elementAt(3));
			} catch (NumberFormatException e) {
				item.port = 70;
			}
		}
		
		return item;
	}
	
	public static Vector splitString(String text, char separator) {
		Vector output = new Vector();
		
		if (text == null || text.length() == 0) {
			output.addElement("");
			return output;
		}
		
		final int len = text.length();
		int mark = 0;
		int pos = 0;
		
		do {
			while (pos < len && text.charAt(pos) != separator)
				pos++;
			output.addElement(text.substring(mark, pos));
			mark = ++pos; // Exactly one tab between each field.
		} while (pos < len);
		
		return output;
	}
	
	public static int numPages(int total_size, int page_size) {
		if (total_size % page_size == 0)
			return total_size / page_size;
		else
			return total_size / page_size + 1;
	}

	// Based on code by Nuno J. Silva <gopher://sdf-eu.org/1/users/njsg>
	public static DirectoryItem parseGopherURL(String gopher_url) {
		if (gopher_url == null) return null;
		
		String hostname, selector;
		int port = 70;
		char type = '1';
		
		int position = 0;
		if (gopher_url.startsWith("gopher://"))	position = 9;

		{ 
			int next_slash = gopher_url.indexOf('/', position);
			int next_colon = gopher_url.indexOf(':', position);
			int end_of_host;

			if (next_slash == -1) next_slash = gopher_url.length();
			if ((next_colon < next_slash) && (next_colon != -1)) {
				// Then we have a port field
				end_of_host = next_colon;
				port = Integer.parseInt(
					gopher_url.substring(
						next_colon + 1, next_slash));
			} else { 
				end_of_host = next_slash;
			}
			
			hostname = gopher_url.substring(position, end_of_host);
			position = next_slash + 1;
		}

		if (position < gopher_url.length()) {
			type = gopher_url.charAt(position);
			position += 2; // Skip over type AND subsequent slash.
		} 
		if (position < gopher_url.length())
			selector = gopher_url.substring(position);
		else
			selector = "";
		
		return new DirectoryItem(type, null, selector, hostname, port);
	}
}
