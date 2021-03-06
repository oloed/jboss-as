/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.ejb3.deployment.processors;

import static org.jboss.as.ee.component.Attachments.EE_MODULE_CONFIGURATION;
import static org.jboss.as.server.deployment.Attachments.MODULE;

import java.util.ArrayList;
import java.util.List;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.ee.component.ComponentConfiguration;
import org.jboss.as.ee.component.ComponentDescription;
import org.jboss.as.ee.component.EEModuleConfiguration;
import org.jboss.as.ejb3.component.EJBComponent;
import org.jboss.as.ejb3.component.EJBComponentDescription;
import org.jboss.as.ejb3.subsystem.EJB3Extension;
import org.jboss.as.ejb3.subsystem.deployment.AbstractEJBComponentRuntimeHandler;
import org.jboss.as.ejb3.subsystem.deployment.EJBComponentType;
import org.jboss.as.server.deployment.AttachmentKey;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.Phase;
import org.jboss.logging.Logger;
import org.jboss.modules.Module;

/**
 * {@link Phase#INSTALL} processor that adds management resources describing EJB components.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class EjbManagementDeploymentUnitProcessor implements DeploymentUnitProcessor {

    private static final Logger log = Logger.getLogger(EjbManagementDeploymentUnitProcessor.class);

    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {
        final DeploymentUnit deploymentUnit = phaseContext.getDeploymentUnit();
        final EEModuleConfiguration moduleDescription = deploymentUnit.getAttachment(EE_MODULE_CONFIGURATION);
        if (moduleDescription == null) {
            // Nothing to do
            return;
        }
        if (deploymentUnit.getParent() != null && deploymentUnit.getParent().getParent() != null) {
            // We only expose management resources 2 levels deep
            return;
        }

        // Iterate through each component, installing it into the container
        for (ComponentConfiguration configuration : moduleDescription.getComponentConfigurations()) {
            try {
                ComponentDescription componentDescription = configuration.getComponentDescription();
                if (componentDescription instanceof EJBComponentDescription) {
                    installManagementResource(configuration, deploymentUnit);
                }
            } catch (RuntimeException e) {
                throw new DeploymentUnitProcessingException("Failed to install management resources for " + configuration, e);
            }
        }
    }

    @Override
    public void undeploy(DeploymentUnit deploymentUnit) {
        final EEModuleConfiguration moduleDescription = deploymentUnit.getAttachment(EE_MODULE_CONFIGURATION);
        if (moduleDescription == null) {
            // Nothing to do
            return;
        }
        if (deploymentUnit.getParent() != null && deploymentUnit.getParent().getParent() != null) {
            // We only expose management resources 2 levels deep
            return;
        }

        // Iterate through each component, installing it into the container
        for (ComponentConfiguration configuration : moduleDescription.getComponentConfigurations()) {
            try {
                ComponentDescription componentDescription = configuration.getComponentDescription();
                if (componentDescription instanceof EJBComponentDescription) {
                    uninstallManagementResource(configuration, deploymentUnit);
                }
            } catch (RuntimeException e) {
                log.error(String.format("Failed to remove management resources for %s -- %s", configuration, e));
            }
        }
    }

    private void installManagementResource(ComponentConfiguration configuration, DeploymentUnit deploymentUnit) {
        EJBComponentType type = EJBComponentType.getComponentType(configuration);
        PathAddress addr = getComponentAddress(type, configuration, deploymentUnit);
        final AbstractEJBComponentRuntimeHandler handler = type.getRuntimeHandler();
        handler.registerComponent(addr, configuration);

        deploymentUnit.createDeploymentSubModel(EJB3Extension.SUBSYSTEM_NAME, addr.getLastElement());
    }

    private void uninstallManagementResource(ComponentConfiguration configuration, DeploymentUnit deploymentUnit) {
        EJBComponentType type = EJBComponentType.getComponentType(configuration);
        PathAddress addr = getComponentAddress(type, configuration, deploymentUnit);
        final AbstractEJBComponentRuntimeHandler handler = type.getRuntimeHandler();
        handler.unregisterComponent(addr);
    }

    private static PathAddress getComponentAddress(EJBComponentType type, ComponentConfiguration configuration, DeploymentUnit deploymentUnit) {
        List<PathElement> elements = new ArrayList<PathElement>();
        if (deploymentUnit.getParent() == null) {
            elements.add(PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT, deploymentUnit.getName()));
        } else {
            elements.add(PathElement.pathElement(ModelDescriptionConstants.DEPLOYMENT, deploymentUnit.getParent().getName()));
            elements.add(PathElement.pathElement(ModelDescriptionConstants.SUBDEPLOYMENT, deploymentUnit.getName()));
        }
        elements.add(PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, EJB3Extension.SUBSYSTEM_NAME));
        elements.add(PathElement.pathElement(type.getResourceType(), configuration.getComponentName()));
        return PathAddress.pathAddress(elements);
    }
}
