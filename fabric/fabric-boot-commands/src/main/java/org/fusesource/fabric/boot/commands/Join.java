/**
 * Copyright (C) FuseSource, Inc.
 * http://fusesource.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.fabric.boot.commands;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryOneTime;
import org.apache.felix.gogo.commands.Argument;
import org.apache.felix.gogo.commands.Command;
import org.apache.felix.gogo.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.apache.zookeeper.KeeperException;
import org.fusesource.fabric.api.ContainerOptions;
import org.fusesource.fabric.utils.BundleUtils;
import org.fusesource.fabric.utils.Ports;
import org.fusesource.fabric.utils.SystemProperties;
import org.fusesource.fabric.utils.shell.ShellUtils;
import org.fusesource.fabric.zookeeper.ZkDefs;
import org.fusesource.fabric.zookeeper.ZkPath;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.service.cm.ConfigurationAdmin;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;

import static org.fusesource.fabric.zookeeper.utils.ZooKeeperUtils.exists;

@Command(name = "join", scope = "fabric", description = "Join a container to an existing fabric", detailedDescription = "classpath:join.txt")
public class Join extends OsgiCommandSupport implements org.fusesource.fabric.boot.commands.service.Join {

    ConfigurationAdmin configurationAdmin;
    private BundleContext bundleContext;

    @Option(name = "-n", aliases = "--non-managed", multiValued = false, description = "Flag to keep the container non managed")
    private boolean nonManaged;

    @Option(name = "-f", aliases = "--force", multiValued = false, description = "Forces the use of container name")
    private boolean force;

    @Option(name = "-p", aliases = "--profile", multiValued = false, description = "Chooses the profile of the container")
    private String profile = "fabric";

    @Option(name = "-v", aliases = "--version", multiValued = false, description = "Chooses the version of the container.")
    private String version = ContainerOptions.DEFAULT_VERSION;

    @Option(name = "--min-port", multiValued = false, description = "The minimum port of the allowed port range")
    private int minimumPort = Ports.MIN_PORT_NUMBER;

    @Option(name = "--max-port", multiValued = false, description = "The maximum port of the allowed port range")
    private int maximumPort = Ports.MAX_PORT_NUMBER;

    @Argument(required = true, index = 0, multiValued = false, description = "Zookeeper URL")
    private String zookeeperUrl;

    @Option(name = "-r", aliases = {"--resolver"}, description = "The resolver policy. Possible values are: localip, localhostname, publicip, publichostname, manualip. Default is localhostname.")
    String resolver;

    @Option(name = "-b", aliases = {"--bind-address"}, description = "The default bind address.")
    String bindAddress;

    @Option(name = "-m", aliases = {"--manual-ip"}, description = "An address to use, when using the manualip resolver.")
    String manualIp;

    @Option(name = "--zookeeper-password", multiValued = false, description = "The ensemble password to use (one will be generated if not given)")
    private String zookeeperPassword;

    @Argument(required = false, index = 1, multiValued = false, description = "Container name to use in fabric. By default a karaf name will be used")
    private String containerName;


    @Override
    protected Object doExecute() throws Exception {
        String oldName = System.getProperty(SystemProperties.KARAF_NAME);
        if (containerName == null) {
            containerName = oldName;
        }

        if (resolver != null) {
            System.setProperty(ZkDefs.LOCAL_RESOLVER_PROPERTY, resolver);
        }

        if (manualIp != null) {
            System.setProperty(ZkDefs.MANUAL_IP, manualIp);
        }

        if (bindAddress != null) {
            System.setProperty(ZkDefs.BIND_ADDRESS, bindAddress);
        }

        zookeeperPassword = zookeeperPassword != null ? zookeeperPassword : ShellUtils.retrieveFabricZookeeperPassword(session);
        System.setProperty(ZkDefs.MINIMUM_PORT, String.valueOf(minimumPort));
        System.setProperty(ZkDefs.MAXIMUM_PORT, String.valueOf(maximumPort));

        if (!containerName.equals(oldName)) {
            if (force || permissionToRenameContainer()) {
                if (!registerContainer(containerName, zookeeperPassword, profile, force)) {
                    System.err.print("A container with the name: " + containerName + " is already member of the cluster. You can specify a different name as an argument.");
                    return null;
                }

                System.setProperty(SystemProperties.KARAF_NAME, containerName);
                System.setProperty("zookeeper.url", zookeeperUrl);
                System.setProperty("zookeeper.password", zookeeperPassword);
                //Rename the container
                File file = new File(System.getProperty("karaf.base") + "/etc/system.properties");
                org.apache.felix.utils.properties.Properties props = new org.apache.felix.utils.properties.Properties(file);
                props.put(SystemProperties.KARAF_NAME, containerName);
                //Also pass zookeeper information so that the container can auto-join after the restart.
                props.put("zookeeper.url", zookeeperUrl);
                props.put("zookeeper.password", zookeeperPassword);
                props.save();

                if (!nonManaged) {
                    installBundles();
                }
                //Restart the container
                System.setProperty("karaf.restart", "true");
                System.setProperty("karaf.restart.clean", "false");
                bundleContext.getBundle(0).stop();

                return null;
            } else {
                return null;
            }
        } else {
            if (!registerContainer(containerName, zookeeperPassword, profile, force)) {
                System.err.println("A container with the name: " + containerName + " is already member of the cluster. You can specify a different name as an argument.");
                return null;
            }
            org.osgi.service.cm.Configuration config = configurationAdmin.getConfiguration("org.fusesource.fabric.zookeeper");
            Hashtable<String, Object> properties = new Hashtable<String, Object>();
            properties.put("zookeeper.url", zookeeperUrl);
            properties.put("zookeeper.password", zookeeperPassword);
            config.setBundleLocation(null);
            config.update(properties);
            if (!nonManaged) {
                installBundles();
            }
            return null;
        }
    }

    /**
     * Checks if there is an existing container using the same name.
     *
     * @param name
     * @return
     * @throws InterruptedException
     * @throws KeeperException
     */
    private boolean registerContainer(String name, String registryPassword, String profile, boolean force) throws Exception {
        boolean exists = false;
        CuratorFramework curator = null;
        try {

            CuratorFrameworkFactory.Builder builder = CuratorFrameworkFactory.builder()
                    .connectString(zookeeperUrl)
                    .retryPolicy(new RetryOneTime(1000))
                    .connectionTimeoutMs(60000);

            if (registryPassword != null && !registryPassword.isEmpty()) {
                builder.authorization("digest", ("fabric:" + registryPassword).getBytes());
            }

            curator = builder.build();
            curator.start();
            curator.getZookeeperClient().blockUntilConnectedOrTimedOut();
            exists = exists(curator, ZkPath.CONTAINER.getPath(name)) != null;
            if (!exists || force) {
                ZkPath.createContainerPaths(curator, containerName, version, profile);
            }
        } finally {
            if (curator != null) {
                curator.close();
            }
        }
        return !exists || force;
    }

    /**
     * Asks the users permission to restart the container.
     *
     * @return
     * @throws IOException
     */
    private boolean permissionToRenameContainer() throws IOException {
        for (; ; ) {
            StringBuffer sb = new StringBuffer();
            System.err.println("You are about to change the container name. This action will restart the container.");
            System.err.println("The local shell will automatically restart, but ssh connections will be terminated.");
            System.err.println("The container will automatically join: " + zookeeperUrl + " the cluster after it restarts.");
            System.err.print("Do you wish to proceed (yes/no):");
            System.err.flush();
            for (; ; ) {
                int c = session.getKeyboard().read();
                if (c < 0) {
                    return false;
                }
                System.err.print((char) c);
                if (c == '\r' || c == '\n') {
                    break;
                }
                sb.append((char) c);
            }
            String str = sb.toString();
            if ("yes".equals(str)) {
                return true;
            }
            if ("no".equals(str)) {
                return false;
            }
        }
    }

    public void installBundles() throws BundleException {
        BundleUtils bundleUtils = new BundleUtils(bundleContext);
        Bundle bundleFabricCommands = bundleUtils.findBundle("org.fusesource.fabric.fabric-commands");
        bundleFabricCommands.start();

        if (!nonManaged) {
            Bundle bundleFabricConfigAdmin = bundleUtils.findBundle("org.fusesource.fabric.fabric-configadmin");
            Bundle bundleFabricAgent = bundleUtils.findBundle("org.fusesource.fabric.fabric-agent");
            bundleFabricConfigAdmin.start();
            bundleFabricAgent.start();
        }
    }


    @Override
    public Object run() throws Exception {
        return doExecute();
    }

    @Override
    public void setConfigurationAdmin(ConfigurationAdmin configurationAdmin) {
        this.configurationAdmin = configurationAdmin;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getZookeeperUrl() {
        return zookeeperUrl;
    }

    @Override
    public void setZookeeperUrl(String zookeeperUrl) {
        this.zookeeperUrl = zookeeperUrl;
    }

    public boolean isNonManaged() {
        return nonManaged;
    }

    public void setNonManaged(boolean nonManaged) {
        this.nonManaged = nonManaged;
    }

    public boolean isForce() {
        return force;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }


    public String getResolver() {
        return resolver;
    }

    public void setResolver(String resolver) {
        this.resolver = resolver;
    }

    public String getManualIp() {
        return manualIp;
    }

    public void setManualIp(String manualIp) {
        this.manualIp = manualIp;
    }

    public String getZookeeperPassword() {
        return zookeeperPassword;
    }

    public void setZookeeperPassword(String zookeeperPassword) {
        this.zookeeperPassword = zookeeperPassword;
    }
}
