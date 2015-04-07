# 52°North TerrainServer
README file for the [52°North TerrainServer][1]

The 52°North terrainServer is a Web application providing access to elevation information, in other words, z-value information is processed. 
In addition to topographic elevations, these values might represent other thematic contents, such as sea depth values, groundwater levels, 
pollutant concentrations, meteorological parameters, etc.
Perspective map views can be delivered. Access to elevation data, 3D scene representations, and rendered images occurs via HTTP-Get services and well-specified service interfaces. 
Thus, functionality provided by the 52°North terrainServer can be easily integrated into existing spatial information infrastructures. 

## Basic functionality:
* based on the Triturus library available at https://github.com/52North/triturus.git 
* HTTP Get service to query elevation values for a specified position (x, y)
* Service to derive cross-section data from elevation grid models
* Prototype Web 3D Scene Service (W3DS) for terrain surfaces
* Prototype Web Terrain Service (WTS) implementation to generate perspective map views 

## Services
* The WebTerrainServlet, which offers
** a Web Terrain Service (WTS) implementation to generate perspective views, e.g. a rendered PNG or JPEG image showing a static 3D view of the Earth's surface;
* the ProfileServlet, which offers
** a simple HTTP-Get service to query an elevation value for a specified position (x, y), as well as
** a service to derive cross-sections from the underlying elevation data;
* the DEMServlet, which implements
** a Web 3D Scene Service (W3DS) prototype delivering dynamic 3D scene descriptions, e.g. as VRML or X3D document, as well as
** services delivering elevation models encoded in selectable ASCII data formats.

It is possible to install all of the services mentioned above or just single services.

## Installation
An installation guide can be found on the following link: http://52north.org/files/3d-community/installation_guides/52n%20terrainServer%201%200%202%20%20Installation%20Guide.pdf 

## License information
This program is free software; you can redistribute it and/or modify it
under the terms of the GNU General Public License version 2 as published
by the Free Software Foundation.

For further information please refer to 'LICENSE'-file

## Additional documents and links
This sections lists documents that lead to a deeper understanding of the TerrainServer and give additional information

* White Paper of Triturus Framework: http://52north.org/images/stories/52n/communities/3D/triturus%20white%20paper.pdf 
* 52°North 3D Community Wiki: https://wiki.52north.org/bin/view/V3d/ 
* TerrainServer Wiki: https://wiki.52north.org/bin/view/V3d/TerrainServer 
* Specification of the Web 3D Scene-Graph Service: http://www.geoinformatik-bochum.de/share/ct_W3DS_DGM50_1.0.0_SDI-Suite.PDF
* Specification of the 52N terrainServer's Cross-Section Service: http://www.geoinformatik-bochum.de/share/ct_ProfileService_DGM50_0.1.0_terrainServer.pdf

## Contributing

You are interesting in contributing the 52°North TerrainServer and you want to pull your changes to the 52N repository to make it available to all?

In that case we need your official permission and for this purpose we have a so called contributors license agreement (CLA) in place. With this agreement you grant us the rights to use and publish your code under an open source license.

A link to the contributors license agreement and further explanations are available here: 

    http://52north.org/about/licensing/cla-guidelines

## Support and Contact

You can get support in the community mailing list and forums:

    http://52north.org/resources/mailing-lists-and-forums/

If you encounter any issues with the software or if you would like to see
certain functionality added, let us know at:

 - Benno Schmidt (b.schmidt@52north.org)
 - Martin May (m.may@52north.org)
 - Christian Danowski (christian.danowski@hs-bochum.de)

The 3D Community

52°North Inititative for Geospatial Open Source Software GmbH, Germany

--
[1]: http://52north.org/communities/3d/terrainserver
