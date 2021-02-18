GIS data
========
This folder contains utilities to build SVG files for maps. They are
needed for scenario5 to display a kartograph_ map.

.. _kartograph: http://kartograph.org/


Requirements
============
To build maps you need the following:

1. The `ne_50m_admin_0_countries` dataset from NaturalEarthData_.
   Download and unpack it in the ``natearth`` subfolder. The shapefile
   should end up at ``gis/natearth/ne_50m_admin_0_countries.shp``.

2. A Python interpreter (consider using virtualenv_) with the following
   libraries installed::

    gdal
    lxml==3.0.2
    Shapely==1.2.16
    kartograph.py==0.6.2
    pykml==0.1.0
    pyproj==1.9.3
    pyshp==1.1.4
    tinycss==0.3


   Then install Kartograph from GitHub::

    $ pip install https://github.com/kartograph/kartograph.py/zipball/master

.. _naturalearthdata: http://www.naturalearthdata.com/
.. _virtualenv: http://www.virtualenv.org/


Building
========
The SVG should build with one simple command::

    $ kartograph europe.json -o ../scoreboard/static/europe.svg
