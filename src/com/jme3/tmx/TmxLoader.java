package com.jme3.tmx;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetLoader;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.shape.Curve;
import com.jme3.texture.Texture.MagFilter;
import com.jme3.texture.Texture.WrapMode;
import com.jme3.texture.Texture2D;
import com.jme3.tmx.core.Layer;
import com.jme3.tmx.core.ObjectLayer;
import com.jme3.tmx.core.ObjectNode;
import com.jme3.tmx.core.Tile;
import com.jme3.tmx.core.TileLayer;
import com.jme3.tmx.core.TiledMap;
import com.jme3.tmx.core.TiledMap.Orientation;
import com.jme3.tmx.core.Tileset;
import com.jme3.tmx.math2d.Ellipse;
import com.jme3.tmx.math2d.Polygon;
import com.jme3.tmx.util.Base64;
import com.jme3.tmx.util.ColorUtil;
import com.jme3.tmx.util.TileGeom;
import com.sun.istack.internal.logging.Logger;

public class TmxLoader implements AssetLoader {

	static Logger logger = Logger.getLogger(TmxLoader.class);

	private AssetManager assetManager;
	private AssetKey<?> key;

	private TiledMap map;
	private String xmlPath;
	private TreeMap<Integer, Tileset> tilesetPerFirstGid;

	@Override
	public Object load(AssetInfo assetInfo) throws IOException {
		key = assetInfo.getKey();
		assetManager = assetInfo.getManager();

		String extension = key.getExtension();

		switch (extension) {
		case "tmx":
			return loadMap(assetInfo.openStream());
		case "tsx":
			return loadTileSet(assetInfo.openStream());
		default:
			return null;
		}

	}

	/**
	 * Load a Map from .tmx file
	 * 
	 * @param inputStream
	 * @return
	 * @throws IOException
	 */
	private TiledMap loadMap(InputStream inputStream) throws IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document doc;
		try {
			factory.setIgnoringComments(true);
			factory.setIgnoringElementContentWhitespace(true);
			factory.setExpandEntityReferences(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setEntityResolver(new EntityResolver() {
				@Override
				public InputSource resolveEntity(String publicId,
						String systemId) {
					if (systemId.equals("http://mapeditor.org/dtd/1.0/map.dtd")) {
						return new InputSource(getClass().getResourceAsStream(
								"resources/map.dtd"));
					}
					return null;
				}
			});

			InputSource insrc = new InputSource(inputStream);
			insrc.setSystemId(key.getFolder());
			insrc.setEncoding("UTF-8");
			doc = builder.parse(insrc);
		} catch (SAXException e) {
			e.printStackTrace();
			throw new RuntimeException("Error while parsing map file: "
					+ e.toString());
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}

		try {
			readMap(doc);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return map;
	}

	/**
	 * Load a TileSet from .tsx file.
	 * 
	 * @param inputStream
	 * @return
	 */
	private Tileset loadTileSet(final InputStream inputStream) {
		Tileset set = null;
		Node tsNode;

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		Document doc;
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			doc = builder.parse(inputStream);

			NodeList tsNodeList = doc.getElementsByTagName("tileset");

			// There can be only one tileset in a .tsx file.
			tsNode = tsNodeList.item(0);

			if (tsNode != null) {
				set = readTileset(tsNode);
				if (set.getSource() != null) {
					logger.warning("Recursive external tilesets are not supported.");
				}
				set.setSource(key.getName());
			}
		} catch (Exception e) {
			logger.warning("Failed while loading " + key.getName(), e);
		}

		return set;
	}

	/**
	 * Load TileSet from a ".tsx" file.
	 * 
	 * @param source
	 * @return
	 */
	private Tileset loadTileSet(final String source) {
		String assetPath = toJmeAssetPath(source);

		// load it with assetManager
		Tileset ext = null;
		try {
			ext = (Tileset) assetManager.loadAsset(assetPath);
		} catch (Exception e) {
			logger.warning("Tileset " + source + " was not loaded correctly!",
					e);
		}

		return ext;
	}

	private static String getAttributeValue(Node node, String attribname) {
		final NamedNodeMap attributes = node.getAttributes();
		String value = null;
		if (attributes != null) {
			Node attribute = attributes.getNamedItem(attribname);
			if (attribute != null) {
				value = attribute.getNodeValue();
			}
		}
		return value;
	}

	private static int getAttribute(Node node, String attribname, int def) {
		final String attr = getAttributeValue(node, attribname);
		if (attr != null) {
			return Integer.parseInt(attr);
		} else {
			return def;
		}
	}

	private static double getDoubleAttribute(Node node, String attribname,
			double def) {
		final String attr = getAttributeValue(node, attribname);
		if (attr != null) {
			return Double.parseDouble(attr);
		} else {
			return def;
		}
	}

	private void readMap(Document doc) throws Exception {
		Node item, mapNode;

		mapNode = doc.getDocumentElement();

		if (!"map".equals(mapNode.getNodeName())) {
			throw new Exception("Not a valid tmx map file.");
		}

		// Get the map dimensions and create the map
		int mapWidth = getAttribute(mapNode, "width", 0);
		int mapHeight = getAttribute(mapNode, "height", 0);

		if (mapWidth > 0 && mapHeight > 0) {
			map = new TiledMap(mapWidth, mapHeight);
		} else {
			// Maybe this map is still using the dimensions element
			NodeList l = doc.getElementsByTagName("dimensions");
			for (int i = 0; (item = l.item(i)) != null; i++) {
				if (item.getParentNode() == mapNode) {
					mapWidth = getAttribute(item, "width", 0);
					mapHeight = getAttribute(item, "height", 0);

					if (mapWidth > 0 && mapHeight > 0) {
						map = new TiledMap(mapWidth, mapHeight);
					}
				}
			}
		}

		if (map == null) {
			logger.warning("Couldn't locate map dimensions.");
			throw new RuntimeException("Couldn't locate map dimensions.");
		}

		// Load other map attributes
		String orientation = getAttributeValue(mapNode, "orientation");
		int tileWidth = getAttribute(mapNode, "tilewidth", 0);
		int tileHeight = getAttribute(mapNode, "tileheight", 0);
		int hexsidelength = getAttribute(mapNode, "hexsidelength", 0);
		String staggerAxis = getAttributeValue(mapNode, "staggeraxis");
		String staggerIndex = getAttributeValue(mapNode, "staggerindex");
		String bgStr = getAttributeValue(mapNode, "backgroundcolor");

		if (orientation != null) {
			map.setOrientation(orientation.toUpperCase());
		} else {
			map.setOrientation(Orientation.ORTHOGONAL);
		}

		if (tileWidth > 0) {
			map.setTileWidth(tileWidth);
		}
		if (tileHeight > 0) {
			map.setTileHeight(tileHeight);
		}
		if (hexsidelength > 0) {
			map.setHexSideLength(hexsidelength);
		}

		if (staggerAxis != null) {
			map.setStaggerAxis(staggerAxis);
		}

		if (staggerIndex != null) {
			map.setStaggerIndex(staggerIndex);
		}

		ColorRGBA backgroundColor = null;
		if (bgStr != null) {
			backgroundColor = ColorUtil.toColorRGBA(bgStr);
			map.setBackgroundColor(backgroundColor);
		}

		// Load properties
		readProperties(mapNode.getChildNodes(), map.getProperties());

		// Load tilesets first, in case order is munged
		tilesetPerFirstGid = new TreeMap<>();
		NodeList l = doc.getElementsByTagName("tileset");
		for (int i = 0; (item = l.item(i)) != null; i++) {
			map.addTileset(readTileset(item));
		}

		// Load the layers and objectgroups
		for (Node sibs = mapNode.getFirstChild(); sibs != null; sibs = sibs
				.getNextSibling()) {
			if ("layer".equals(sibs.getNodeName())) {
				Layer layer = readTileLayer(sibs);
				if (layer != null) {
					map.addLayer(layer);
				}
			} else if ("objectgroup".equals(sibs.getNodeName())) {
				Layer layer = readObjectLayer(sibs);
				if (layer != null) {
					map.addLayer(layer);
				}
			} else {
			}
		}
		tilesetPerFirstGid = null;
	}

	private Tileset readTileset(Node t) throws Exception {
		Tileset set = null;

		String source = getAttributeValue(t, "source");
		String basedir = key.getFolder();// getAttributeValue(t, "basedir");
		int firstGid = getAttribute(t, "firstgid", 1);

		if (source != null) {
			set = loadTileSet(basedir + source);

		} else {

			final int tileWidth = getAttribute(t, "tilewidth",
					map != null ? map.getTileWidth() : 0);
			final int tileHeight = getAttribute(t, "tileheight",
					map != null ? map.getTileHeight() : 0);
			final int tileSpacing = getAttribute(t, "spacing", 0);
			final int tileMargin = getAttribute(t, "margin", 0);

			final String name = getAttributeValue(t, "name");

			set = new Tileset(tileWidth, tileHeight, tileSpacing, tileMargin);

			set.setName(name);

			boolean hasTilesetImage = false;
			NodeList children = t.getChildNodes();

			for (int i = 0; i < children.getLength(); i++) {
				Node child = children.item(i);

				String nodeName = child.getNodeName();
				if (nodeName.equalsIgnoreCase("image")) {
					if (hasTilesetImage) {
						logger.warning("Ignoring illegal image element after tileset image.");
						continue;
					}
					
					AnImage image = readImage(child, basedir);
					if (image.texture != null) {
						// Not a shared image, but an entire set in one image
						// file. There should be only one image element in this
						// case.
						hasTilesetImage = true;

						set.setSource(image.source);
						set.setTexture(image.texture);
						set.setMaterial(image.createMaterial());
					}
				} else if (nodeName.equalsIgnoreCase("terraintypes")) {
					// TODO add support to terraintypes
					logger.info("terraintypes not support yet");
				} else if (nodeName.equalsIgnoreCase("tile")) {
					Tile tile = readTile(set, child, basedir);
					if (!hasTilesetImage || tile.getId() > set.getMaxTileId()) {
						set.addTile(tile);
						/**
						 * Calculate texCoords for each tile, and create a
						 * Geometry for it. TODO : refact this code
						 */
						if (tile.getMaterial() != null) {
							TileGeom sprite = new TileGeom("tile#"
									+ tile.getId());
							sprite.setSize(
									tile.getWidth() / map.getTileWidth(),
									tile.getHeight() / map.getTileHeight());
							sprite.setTexCoordFromTile(tile);

							sprite.setMaterial(tile.getMaterial());
							sprite.setQueueBucket(Bucket.Translucent);

							tile.setGeometry(sprite);
						}
					} else {
						Tile myTile = set.getTile(tile.getId());
						myTile.setProperties(tile.getProperties());
						// TODO: there is the possibility here of overlaying
						// images, which some people may want
					}
				}
			}
		}

		if (set != null) {
			setFirstGidForTileset(set, firstGid);
		}

		return set;
	}

	private Tile readTile(Tileset set, Node t, String baseDir) throws Exception {
		Tile tile = new Tile();
		tile.setTileset(set);

		final int id = getAttribute(t, "id", -1);
		final String terrainStr = getAttributeValue(t, "terrain");
		final double probability = getDoubleAttribute(t, "probability", -1.0);

		if (terrainStr != null) {
			String[] tileIds = terrainStr.split("[\\s]*,[\\s]*");
			int terrain = 0;
			tile.setTerrain(terrain);
		}
		
		// TODO
		tile.setId(id);
		tile.setProbability((float)probability);

		NodeList children = t.getChildNodes();
		readProperties(children, tile.getProperties());

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("image".equalsIgnoreCase(child.getNodeName())) {
				
				AnImage image = readImage(child, baseDir);
				tile.setTexture(image.texture);
				tile.setMaterial(image.createMaterial());

				// TODO remove
				float qx = (float) tile.getWidth() / map.getTileWidth();
				float qy = (float) tile.getHeight() / map.getTileHeight();
				TileGeom sprite = new TileGeom("tile#" + tile.getId());
				sprite.setSize(qx, qy);
				sprite.setTexCoordFromTile(tile);

				sprite.setMaterial(tile.getMaterial());
				sprite.setQueueBucket(Bucket.Translucent);

				tile.setGeometry(sprite);

			} else if ("animation".equalsIgnoreCase(child.getNodeName())) {
				// TODO: fill this in once TMXMapWriter is complete
			}
		}

		return tile;
	}

	/**
	 * load a image from file or decode from the data elements.
	 * 
	 * Note that it is not currently possible to use Tiled to create maps with
	 * embedded image data, even though the TMX format supports this. It is
	 * possible to create such maps using libtiled (Qt/C++) or tmxlib (Python).
	 * 
	 * @param t
	 * @param baseDir
	 * @return
	 * @throws IOException
	 */
	private AnImage readImage(Node t, String baseDir) throws IOException {
		
		AnImage image = new AnImage();

		String source = getAttributeValue(t, "source");
		
		// load a image from file or decode from the CDATA.
		if (source != null) {
			String assetPath = toJmeAssetPath(baseDir + source);
			image.source = assetPath;
			image.texture = loadTexture2D(assetPath);
		} else {
			NodeList nl = t.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				Node node = nl.item(i);
				if ("data".equals(node.getNodeName())) {
					Node cdata = node.getFirstChild();
					if (cdata != null) {
						String sdata = cdata.getNodeValue();
						char[] charArray = sdata.trim().toCharArray();
						byte[] imageData = Base64.decode(charArray);

						image.texture = loadTexture2D(imageData);
					}
					break;
				}
			}
		}
		
		image.format = getAttributeValue(t, "format");
		image.trans = getAttributeValue(t, "trans");
		image.width = getAttribute(t, "width", 0);
		image.height = getAttribute(t, "height", 0);
		
		
		return image;
		
	}

	/**
	 * Loads a map layer from a layer node.
	 * 
	 * @param t
	 *            the node representing the "layer" element
	 * @return the loaded map layer
	 * @throws Exception
	 */
	private Layer readTileLayer(Node t) throws Exception {
		final int layerWidth = getAttribute(t, "width", map.getWidth());
		final int layerHeight = getAttribute(t, "height", map.getHeight());

		TileLayer ml = new TileLayer(layerWidth, layerHeight);

		final int offsetX = getAttribute(t, "x", 0);
		final int offsetY = getAttribute(t, "y", 0);
		final int visible = getAttribute(t, "visible", 1);
		String opacity = getAttributeValue(t, "opacity");

		ml.setName(getAttributeValue(t, "name"));

		if (opacity != null) {
			ml.setOpacity(Float.parseFloat(opacity));
		}

		readProperties(t.getChildNodes(), ml.getProperties());

		for (Node child = t.getFirstChild(); child != null; child = child
				.getNextSibling()) {
			String nodeName = child.getNodeName();
			if ("data".equalsIgnoreCase(nodeName)) {
				String encoding = getAttributeValue(child, "encoding");
				String comp = getAttributeValue(child, "compression");

				if ("base64".equalsIgnoreCase(encoding)) {
					Node cdata = child.getFirstChild();
					if (cdata != null) {
						char[] enc = cdata.getNodeValue().trim().toCharArray();
						byte[] dec = Base64.decode(enc);

						InputStream is;
						if ("gzip".equalsIgnoreCase(comp)) {
							final int len = layerWidth * layerHeight * 4;
							is = new GZIPInputStream(new ByteArrayInputStream(
									dec), len);
						} else if ("zlib".equalsIgnoreCase(comp)) {
							is = new InflaterInputStream(
									new ByteArrayInputStream(dec));
						} else if (comp != null && !comp.isEmpty()) {
							throw new IOException(
									"Unrecognized compression method \"" + comp
											+ "\" for map layer "
											+ ml.getName());
						} else {
							is = new ByteArrayInputStream(dec);
						}

						for (int y = 0; y < ml.getHeight(); y++) {
							for (int x = 0; x < ml.getWidth(); x++) {
								int tileId = 0;
								tileId |= is.read();
								tileId |= is.read() << 8;
								tileId |= is.read() << 16;
								tileId |= is.read() << 24;

								setTileAtFromTileId(ml, y, x, tileId);
							}
						}
					}
				} else if ("csv".equalsIgnoreCase(encoding)) {
					String csvText = child.getTextContent();

					if (comp != null && !comp.isEmpty()) {
						throw new IOException(
								"Unrecognized compression method \"" + comp
										+ "\" for map layer " + ml.getName()
										+ " and encoding " + encoding);
					}

					String[] csvTileIds = csvText.trim() // trim 'space', 'tab',
															// 'newline'. pay
															// attention to
															// additional
															// unicode chars
															// like \u2028,
															// \u2029, \u0085 if
															// necessary
							.split("[\\s]*,[\\s]*");

					if (csvTileIds.length != ml.getHeight() * ml.getWidth()) {
						throw new IOException(
								"Number of tiles does not match the layer's width and height");
					}

					for (int y = 0; y < ml.getHeight(); y++) {
						for (int x = 0; x < ml.getWidth(); x++) {
							String sTileId = csvTileIds[x + y * ml.getWidth()];
							int tileId = Integer.parseInt(sTileId);

							setTileAtFromTileId(ml, y, x, tileId);
						}
					}
				} else {
					int x = 0, y = 0;
					for (Node dataChild = child.getFirstChild(); dataChild != null; dataChild = dataChild
							.getNextSibling()) {
						if ("tile".equalsIgnoreCase(dataChild.getNodeName())) {
							int tileId = getAttribute(dataChild, "gid", -1);
							setTileAtFromTileId(ml, y, x, tileId);

							x++;
							if (x == ml.getWidth()) {
								x = 0;
								y++;
							}
							if (y == ml.getHeight()) {
								break;
							}
						}
					}
				}
			} else if ("tileproperties".equalsIgnoreCase(nodeName)) {
				for (Node tpn = child.getFirstChild(); tpn != null; tpn = tpn
						.getNextSibling()) {
					if ("tile".equalsIgnoreCase(tpn.getNodeName())) {
						int x = getAttribute(tpn, "x", -1);
						int y = getAttribute(tpn, "y", -1);

						Properties tip = new Properties();

						readProperties(tpn.getChildNodes(), tip);
						ml.setTileInstancePropertiesAt(x, y, tip);
					}
				}
			}
		}

		// This is done at the end, otherwise the offset is applied during
		// the loading of the tiles.
		ml.setOffset(offsetX, offsetY);

		// Invisible layers are automatically locked, so it is important to
		// set the layer to potentially invisible _after_ the layer data is
		// loaded.
		// todo: Shouldn't this be just a user interface feature, rather than
		// todo: something to keep in mind at this level?
		ml.setVisible(visible == 1);

		return ml;
	}

	private Layer readObjectLayer(Node node) throws Exception {

		final String name = getAttributeValue(node, "name");
		final String color = getAttributeValue(node, "color");
		final int width = getAttribute(node, "width", map.getWidth());
		final int height = getAttribute(node, "height", map.getHeight());
		final String opacity = getAttributeValue(node, "opacity");
		final int visible = getAttribute(node, "visible", 1);
		final int offsetX = getAttribute(node, "x", 0);
		final int offsetY = getAttribute(node, "y", 0);
		final String draworder = getAttributeValue(node, "draworder");

		ObjectLayer og = new ObjectLayer(width, height);

		og.setName(name);
		if (color != null) {
			og.setColor(ColorUtil.toColorRGBA(color));
		}

		if (opacity != null) {
			og.setOpacity(Float.parseFloat(opacity));
		}
		if (draworder != null) {
			logger.info("draworder:" + draworder);
			og.setDraworder(draworder);
		}
		og.setVisible(visible == 1);
		og.setOffset(offsetX, offsetY);

		readProperties(node.getChildNodes(), og.getProperties());

		// Add all objects from the objects group
		NodeList children = node.getChildNodes();

		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("object".equalsIgnoreCase(child.getNodeName())) {
				og.add(readObjectNode(child));
			}
		}

		return og;
	}

	private ObjectNode readObjectNode(Node node) throws Exception {
		final String name = getAttributeValue(node, "name");
		final String type = getAttributeValue(node, "type");
		final String gid = getAttributeValue(node, "gid");
		final double x = getDoubleAttribute(node, "x", 0);
		final double y = getDoubleAttribute(node, "y", 0);
		final double width = getDoubleAttribute(node, "width", 0);
		final double height = getDoubleAttribute(node, "height", 0);

		ObjectNode obj = new ObjectNode(x, y, width, height);
		if (name != null) {
			obj.setName(name);
		}
		if (type != null) {
			obj.setType(type);
		}
		if (gid != null) {
			// TODO fix it with 0x20000000
			Tile tile = getTileForTileGID((int) Long.parseLong(gid) & 0xFFFFFFFF);
			obj.setTile(tile);
		}

		NodeList children = node.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("image".equalsIgnoreCase(child.getNodeName())) {
				String source = getAttributeValue(child, "source");
				if (source != null) {
					if (!new File(source).isAbsolute()) {
						source = xmlPath + source;
					}
					obj.setImageSource(source);
				}
				break;
			} else if ("ellipse".equalsIgnoreCase(child.getNodeName())) {
				obj.setShape(new Ellipse(x, y, width, height));
			} else if ("polygon".equalsIgnoreCase(child.getNodeName())
					|| "polyline".equalsIgnoreCase(child.getNodeName())) {

				List<Vector3f> points = new ArrayList<Vector3f>();
				final String pointsAttribute = getAttributeValue(child,
						"points");
				StringTokenizer st = new StringTokenizer(pointsAttribute, ", ");
				while (st.hasMoreElements()) {
					double pointX = Double.parseDouble(st.nextToken());
					double pointY = Double.parseDouble(st.nextToken());

					Vector3f p = new Vector3f();
					p.x = (float) (x + pointX);
					p.y = (float) (y + pointY);
					p.z = 0;

					points.add(p);
				}

				if (points.size() > 0) {
					logger.info(points.size() + " size");
					points.add(points.get(0));
					logger.info(points.size() + " size");
					Curve curse = new Curve();
					Polygon shape = new Polygon();
					obj.setShape(shape);
				}
			}
		}

		Properties props = new Properties();
		readProperties(children, props);

		obj.setProperties(props);
		return obj;
	}

	/**
	 * Reads properties from amongst the given children. When a "properties"
	 * element is encountered, it recursively calls itself with the children of
	 * this node. This function ensures backward compatibility with tmx version
	 * 0.99a.
	 * 
	 * Support for reading property values stored as character data was added in
	 * Tiled 0.7.0 (tmx version 0.99c).
	 * 
	 * @param children
	 *            the children amongst which to find properties
	 * @param props
	 *            the properties object to set the properties of
	 */
	private void readProperties(NodeList children, Properties props) {
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if ("property".equalsIgnoreCase(child.getNodeName())) {
				final String key = getAttributeValue(child, "name");
				String value = getAttributeValue(child, "value");
				if (value == null) {
					Node grandChild = child.getFirstChild();
					if (grandChild != null) {
						value = grandChild.getNodeValue();
						if (value != null) {
							value = value.trim();
						}
					}
				}
				if (value != null) {
					props.setProperty(key, value);
				}
			} else if ("properties".equals(child.getNodeName())) {
				readProperties(child.getChildNodes(), props);
			}
		}
	}

	/**
	 * Helper method to set the tile based on its global id.
	 * 
	 * @param ml
	 *            tile layer
	 * @param y
	 *            y-coordinate
	 * @param x
	 *            x-coordinate
	 * @param tileId
	 *            global id of the tile as read from the file
	 */
	private void setTileAtFromTileId(TileLayer ml, int y, int x, int tileId) {
		ml.setTileAt(x, y, getTileForTileGID(tileId));
	}

	/**
	 * Helper method to get the tile based on its global id
	 * 
	 * @param tileId
	 *            global id of the tile
	 * @return <ul>
	 *         <li>{@link Tile} object corresponding to the global id, if found</li>
	 *         <li><code>null</code>, otherwise</li>
	 *         </ul>
	 */
	private Tile getTileForTileGID(int tileId) {
		Tile tile = null;
		java.util.Map.Entry<Integer, Tileset> ts = findTileSetForTileGID(tileId);
		if (ts != null) {
			tile = ts.getValue().getTile(tileId - ts.getKey());
		}
		return tile;
	}

	/**
	 * Get the tile set and its corresponding firstgid that matches the given
	 * global tile id.
	 * 
	 * 
	 * @param gid
	 *            a global tile id
	 * @return the tileset containing the tile with the given global tile id, or
	 *         <code>null</code> when no such tileset exists
	 */
	private java.util.Map.Entry<Integer, Tileset> findTileSetForTileGID(int gid) {
		return tilesetPerFirstGid.floorEntry(gid);
	}

	private void setFirstGidForTileset(Tileset tileset, int firstGid) {
		tilesetPerFirstGid.put(firstGid, tileset);
	}

	/**
	 * Load a Texture from source
	 * 
	 * @param source
	 * @return
	 */
	private Texture2D loadTexture2D(final String source) {
		Texture2D tex = null;
		try {
			TextureKey texKey = new TextureKey(source, true);
			texKey.setGenerateMips(false);
			tex = (Texture2D) assetManager.loadTexture(texKey);
			tex.setWrap(WrapMode.Repeat);
			tex.setMagFilter(MagFilter.Nearest);
		} catch (Exception e) {
			logger.warning("Can't load texture " + source, e);
		}

		return tex;
	}

	private Texture2D loadTexture2D(final byte[] data) {
		Class<?> LoaderClass = null;
		Object loaderInstance = null;
		Method loadMethod = null;

		try {
			// try Desktop first
			LoaderClass = Class.forName("com.jme3.texture.plugins.AWTLoader");
		} catch (ClassNotFoundException e) {
			logger.warning("Can't find AWTLoader.");

			try {
				// then try Android Native Image Loader
				LoaderClass = Class
						.forName("com.jme3.texture.plugins.AndroidNativeImageLoader");
			} catch (ClassNotFoundException e1) {
				logger.warning("Can't find AndroidNativeImageLoader.");

				try {
					// then try Android BufferImage Loader
					LoaderClass = Class
							.forName("com.jme3.texture.plugins.AndroidBufferImageLoader");
				} catch (ClassNotFoundException e2) {
					logger.warning("Can't find AndroidNativeImageLoader.");
				}
			}
		}

		if (LoaderClass == null) {
			return null;
		} else {
			// try Desktop first
			try {
				loaderInstance = LoaderClass.newInstance();
				loadMethod = LoaderClass.getMethod("load", AssetInfo.class);
			} catch (ReflectiveOperationException e) {
				logger.warning("Can't find AWTLoader.", e);
			}
		}

		TextureKey texKey = new TextureKey();
		AssetInfo info = new AssetInfo(assetManager, texKey) {
			public InputStream openStream() {
				return new ByteArrayInputStream(data);
			}
		};

		Texture2D tex = null;
		try {
			com.jme3.texture.Image img = (com.jme3.texture.Image) loadMethod
					.invoke(loaderInstance, info);

			tex = new Texture2D();
			tex.setWrap(WrapMode.Repeat);
			tex.setMagFilter(MagFilter.Nearest);
			tex.setAnisotropicFilter(texKey.getAnisotropy());
			tex.setName(texKey.getName());
			tex.setImage(img);
		} catch (Exception e) {
			logger.warning("Can't load texture from byte array", e);
		}

		return tex;

	}

	/**
	 * Utilities method to correct the asset path.
	 * 
	 * @param src
	 * @return
	 */
	private String toJmeAssetPath(final String src) {

		/*
		 * 1st: try to locate it with assetManager. No need to handle the src
		 * path unless assetManager can't locate it.
		 */
		if (assetManager.locateAsset(new AssetKey<Object>(src)) != null) {
			return src;
		}

		/*
		 * 2nd: In JME I suppose that all the files needed are in the same
		 * folder, that's why I cut the filename and contact it to
		 * key.getFolder().
		 */
		String dest = src;
		src.replaceAll("\\\\", "/");
		int idx = src.lastIndexOf("/");
		if (idx >= 0) {
			dest = key.getFolder() + src.substring(idx + 1);
		} else {
			dest = key.getFolder() + dest;
		}

		/*
		 * 3rd: try locate it again.
		 */
		if (assetManager.locateAsset(new AssetKey<Object>(dest)) != null) {
			return dest;
		} else {
			throw new RuntimeException("Can't locate asset: " + src);
		}
	}
	
	/**
	 * When read a &lt;image&gt; element there 5 attribute there.
	 * This class is just a data struct to return the whole image node;
	 * @author yanmaoyuan
	 *
	 */
	private class AnImage {
		// useless for jme3
		String format;
		String source;
		String trans;
		int width;// useless for jme3
		int height;// useless for jme3
		
		Texture2D texture = null;
		
		private Material createMaterial() {
			Material mat = new Material(assetManager, "Shader/TransColor.j3md");
			mat.setTexture("ColorMap", texture);
			mat.setFloat("AlphaDiscardThreshold", 0.01f);

			if (trans != null) {
				ColorRGBA transparentColor = ColorUtil.toColorRGBA(trans);
				mat.setColor("TransColor", transparentColor);
			}
			
			// debug
			// mat.getAdditionalRenderState().setWireframe(true);
			mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);

			return mat;
		}
	}

}
