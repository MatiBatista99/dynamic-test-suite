//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package org.jpos.q2;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import org.jdom2.Element;
import org.jpos.core.Configurable;
import org.jpos.core.Configuration;
import org.jpos.core.ConfigurationException;
import org.jpos.core.Environment;
import org.jpos.core.XmlConfigurable;
import org.jpos.core.annotation.Config;
import org.jpos.q2.qbean.QConfig;
import org.jpos.util.LogSource;
import org.jpos.util.Logger;
import org.jpos.util.NameRegistrar;

public class QFactory {
    ObjectName loaderName;
    Q2 q2;
    ResourceBundle classMapping;
    ConfigurationFactory defaultConfigurationFactory = new SimpleConfigurationFactory();

    public QFactory(ObjectName loaderName, Q2 q2) {
        this.loaderName = loaderName;
        this.q2 = q2;
        this.classMapping = ResourceBundle.getBundle(this.getClass().getName());
    }

    public Object instantiate(Q2 server, Element e) throws ReflectionException, MBeanException, InstanceNotFoundException {
        String clazz = getAttributeValue(e, "class");
        if (clazz == null) {
            try {
                clazz = this.classMapping.getString(e.getName());
            } catch (MissingResourceException var5) {
            }
        }

        MBeanServer mserver = server.getMBeanServer();
        if (!this.q2.isDisableDynamicClassloader()) {
            this.getExtraPath(server.getLoader(), e);
        }

        return mserver.instantiate(clazz, this.loaderName);
    }

    public ObjectInstance createQBean(Q2 server, Element e, Object obj) throws MalformedObjectNameException, InstanceAlreadyExistsException, InstanceNotFoundException, MBeanException, NotCompliantMBeanException, InvalidAttributeValueException, ReflectionException, ConfigurationException {
        String name = getAttributeValue(e, "name");
        if (name == null) {
            name = e.getName();
        }

        ObjectName objectName = new ObjectName("Q2:type=qbean,service=" + name);
        MBeanServer mserver = server.getMBeanServer();
        if (mserver.isRegistered(objectName)) {
            throw new InstanceAlreadyExistsException(name + " has already been deployed in another file.");
        } else {
            ObjectInstance instance = mserver.registerMBean(obj, objectName);

            try {
                this.setAttribute(mserver, objectName, "Name", name);
                String logger = getAttributeValue(e, "logger");
                if (logger != null) {
                    this.setAttribute(mserver, objectName, "Logger", logger);
                }

                String realm = getAttributeValue(e, "realm");
                if (realm != null) {
                    this.setAttribute(mserver, objectName, "Realm", realm);
                }

                this.setAttribute(mserver, objectName, "Server", server);
                this.setAttribute(mserver, objectName, "Persist", e);
                this.configureQBean(mserver, objectName, e);
                this.setConfiguration(obj, e);
                if (obj instanceof QBean) {
                    mserver.invoke(objectName, "init", (Object[])null, (String[])null);
                }

                return instance;
            } catch (Throwable t) {
                mserver.unregisterMBean(objectName);
                t.fillInStackTrace();
                throw t;
            }
        }
    }

    public Q2 getQ2() {
        return this.q2;
    }

    private void getExtraPath(QClassLoader loader, Element e) {
        Element classpathElement = e.getChild("classpath");
        if (classpathElement != null) {
            try {
                loader = loader.scan(true);
            } catch (Throwable t) {
                this.getQ2().getLog().error(t);
            }

            for(Object o : classpathElement.getChildren("url")) {
                Element u = (Element)o;

                try {
                    loader.addURL(u.getTextTrim());
                } catch (MalformedURLException ex) {
                    this.q2.getLog().warn(u.getTextTrim(), ex);
                }
            }
        }

    }

    public void setAttribute(MBeanServer server, ObjectName objectName, String attribute, Object value) throws InstanceNotFoundException, MBeanException, InvalidAttributeValueException, ReflectionException {
        try {
            server.setAttribute(objectName, new Attribute(attribute, value));
        } catch (AttributeNotFoundException var6) {
        } catch (InvalidAttributeValueException var7) {
        }

    }

    public void startQBean(Q2 server, ObjectName objectName) throws InstanceNotFoundException, MBeanException, ReflectionException {
        MBeanServer mserver = server.getMBeanServer();
        mserver.invoke(objectName, "start", (Object[])null, (String[])null);
    }

    public void destroyQBean(Q2 server, ObjectName objectName, Object obj) throws InstanceNotFoundException, MBeanException, ReflectionException {
        MBeanServer mserver = server.getMBeanServer();
        if (obj instanceof QBean) {
            mserver.invoke(objectName, "stop", (Object[])null, (String[])null);
            mserver.invoke(objectName, "destroy", (Object[])null, (String[])null);
        }

        if (objectName != null) {
            mserver.unregisterMBean(objectName);
        }

    }

    public void configureQBean(MBeanServer server, ObjectName objectName, Element e) throws ConfigurationException {
        try {
            for(Object anAttributeList : this.getAttributeList(e)) {
                server.setAttribute(objectName, (Attribute)anAttributeList);
            }

        } catch (Exception e1) {
            throw new ConfigurationException(e1);
        }
    }

    public AttributeList getAttributeList(Element e) throws ConfigurationException {
        AttributeList attributeList = new AttributeList();

        for(Object child : e.getChildren("attr")) {
            Element childElement = (Element)child;
            String name = childElement.getAttributeValue("name");
            name = this.getAttributeName(name);
            Attribute attr = new Attribute(name, this.getObject(childElement));
            attributeList.add(attr);
        }

        return attributeList;
    }

    protected Object getObject(Element childElement) throws ConfigurationException {
        String type = childElement.getAttributeValue("type", "java.lang.String");
        if ("int".equals(type)) {
            type = "java.lang.Integer";
        } else if ("long".equals(type)) {
            type = "java.lang.Long";
        } else if ("boolean".equals(type)) {
            type = "java.lang.Boolean";
        }

        String value = childElement.getText();
        value = Environment.getEnvironment().getProperty(value, value);

        try {
            Class attributeType = Class.forName(type);
            if (Collection.class.isAssignableFrom(attributeType)) {
                return this.getCollection(attributeType, childElement);
            } else {
                Class[] parameterTypes = new Class[]{"".getClass()};
                Object[] parameterValues = new Object[]{value};
                return attributeType.getConstructor(parameterTypes).newInstance(parameterValues);
            }
        } catch (Exception e1) {
            throw new ConfigurationException(e1);
        }
    }

    protected Collection getCollection(Class type, Element e) throws ConfigurationException {
        try {
            Collection<Object> col = (Collection)type.newInstance();

            for(Object o : e.getChildren("item")) {
                col.add(this.getObject((Element)o));
            }

            return col;
        } catch (Exception e1) {
            throw new ConfigurationException(e1);
        }
    }

    public String getAttributeName(String name) {
        if (name == null) {
            throw new NullPointerException("attribute name can not be null");
        } else {
            StringBuilder tmp = new StringBuilder(name);
            if (tmp.length() > 0) {
                tmp.setCharAt(0, name.toUpperCase().charAt(0));
            }

            return tmp.toString();
        }
    }

    public <T> T newInstance(String clazz) throws ConfigurationException {
        try {
            MBeanServer mserver = this.q2.getMBeanServer();
            return (T)mserver.instantiate(clazz, this.loaderName);
        } catch (Exception e) {
            throw new ConfigurationException(clazz, e);
        }
    }

    public <T> T newInstance(Class<T> clazz) throws ConfigurationException {
        return (T)this.newInstance(clazz.getName());
    }

    public Configuration getConfiguration(Element e) throws ConfigurationException {
        String configurationFactoryClazz = getAttributeValue(e, "configuration-factory");
        ConfigurationFactory cf = configurationFactoryClazz != null ? (ConfigurationFactory)this.newInstance(configurationFactoryClazz) : this.defaultConfigurationFactory;
        Configuration cfg = cf.getConfiguration(e);
        String merge = getAttributeValue(e, "merge-configuration");
        if (merge != null) {
            StringTokenizer st = new StringTokenizer(merge, ", ");

            while(st.hasMoreElements()) {
                try {
                    Configuration c = QConfig.getConfiguration(st.nextToken());

                    for(String k : c.keySet()) {
                        if (cfg.get(k, (String)null) == null) {
                            String[] v = c.getAll(k);
                            switch (v.length) {
                                case 0:
                                    break;
                                case 1:
                                    cfg.put(k, v[0]);
                                    break;
                                default:
                                    cfg.put(k, v);
                            }
                        }
                    }
                } catch (NameRegistrar.NotFoundException ex) {
                    throw new ConfigurationException(ex.getMessage());
                }
            }
        }

        return cfg;
    }

    public void setLogger(Object obj, Element e) {
        if (obj instanceof LogSource) {
            String loggerName = getAttributeValue(e, "logger");
            if (loggerName != null) {
                String realm = getAttributeValue(e, "realm");
                if (realm == null) {
                    realm = e.getName();
                }

                Logger logger = Logger.getLogger(loggerName);
                ((LogSource)obj).setLogger(logger, realm);
            }
        }

    }

    public static String getAttributeValue(Element e, String name) {
        String s = e.getAttributeValue(name);
        return Environment.getEnvironment().getProperty(s, s);
    }

    public void setConfiguration(Object obj, Element e, String packagerConfig, String port) throws ConfigurationException {
        try {
            Configuration cfg = this.getConfiguration(e);
            cfg.put("packager-config",packagerConfig);
            cfg.put("port",port);
            autoconfigure(obj, cfg);
            if (obj instanceof Configurable) {
                ((Configurable)obj).setConfiguration(cfg);
            }

            if (obj instanceof XmlConfigurable) {
                ((XmlConfigurable)obj).setConfiguration(e);
            }

        } catch (IllegalAccessException | ConfigurationException ex) {
            throw new ConfigurationException(ex);
        }
    }

    public void setConfiguration(Object obj, Element e) throws ConfigurationException {
        try {
            Configuration cfg = this.getConfiguration(e);
            autoconfigure(obj, cfg);
            if (obj instanceof Configurable) {
                ((Configurable)obj).setConfiguration(cfg);
            }

            if (obj instanceof XmlConfigurable) {
                ((XmlConfigurable)obj).setConfiguration(e);
            }

        } catch (IllegalAccessException | ConfigurationException ex) {
            throw new ConfigurationException(ex);
        }
    }

    public static void invoke(Object obj, String m, Object p) throws ConfigurationException {
        invoke(obj, m, p, p != null ? p.getClass() : null);
    }

    public static void invoke(Object obj, String m, Object p, Class pc) throws ConfigurationException {
        try {
            if (p != null) {
                Class[] paramTemplate = new Class[]{pc};
                Method method = obj.getClass().getMethod(m, paramTemplate);
                Object[] param = new Object[1];
                param[0] = p;
                method.invoke(obj, param);
            } else {
                Method method = obj.getClass().getMethod(m);
                method.invoke(obj);
            }
        } catch (NoSuchMethodException var7) {
        } catch (NullPointerException var8) {
        } catch (IllegalAccessException var9) {
        } catch (InvocationTargetException e) {
            throw new ConfigurationException(obj.getClass().getName() + "." + m + "(" + p + ")", e.getTargetException());
        }

    }

    public static boolean isEnabled(Element e) {
        String enabledAttribute = getEnabledAttribute(e);
        return "true".equalsIgnoreCase(enabledAttribute) || "yes".equalsIgnoreCase(enabledAttribute) || enabledAttribute.contains(Environment.getEnvironment().getName());
    }

    public static String getEnabledAttribute(Element e) {
        return Environment.get(e.getAttributeValue("enabled", "true"));
    }

    public static void autoconfigure(Object obj, Configuration cfg) throws IllegalAccessException {
        Class cc = obj.getClass();

        do {
            Field[] fields = cc.getDeclaredFields();

            for(Field field : fields) {
                if (field.isAnnotationPresent(Config.class)) {
                    Config config = (Config)field.getAnnotation(Config.class);
                    String v = cfg.get(config.value(), (String)null);
                    if (v != null) {
                        if (!field.isAccessible()) {
                            field.setAccessible(true);
                        }

                        Class<?> c = field.getType();
                        if (c.isAssignableFrom(String.class)) {
                            field.set(obj, v);
                        } else if (!c.isAssignableFrom(Integer.TYPE) && !c.isAssignableFrom(Integer.class)) {
                            if (!c.isAssignableFrom(Long.TYPE) && !c.isAssignableFrom(Long.class)) {
                                if (c.isAssignableFrom(Boolean.TYPE) || c.isAssignableFrom(Boolean.class)) {
                                    field.set(obj, cfg.getBoolean(config.value()));
                                }
                            } else {
                                field.set(obj, cfg.getLong(config.value()));
                            }
                        } else {
                            field.set(obj, cfg.getInt(config.value()));
                        }
                    }
                }
            }

            cc = cc.getSuperclass();
        } while(!cc.equals(Object.class));

    }
}

