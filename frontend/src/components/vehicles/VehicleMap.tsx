import { useEffect } from 'react';
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMap } from 'react-leaflet';

/**
 * Thin wrapper around the map library actually in use (Leaflet + OpenStreetMap
 * tiles today). Every other screen imports only this component — swapping to
 * a different map provider later means rewriting this one file, not every
 * screen that shows a vehicle on a map.
 */
export interface MapMarker {
  id: number;
  lat: number;
  lng: number;
  label: string;
  online: boolean;
  heading?: number;
}

export interface RoutePoint {
  lat: number;
  lng: number;
}

function icon(online: boolean, selected: boolean) {
  const color = online ? '#2E7D5B' : '#9AA6A0';
  const size = selected ? 30 : 24;
  return L.divIcon({
    className: '',
    html: `<div style="
      width:${size}px;height:${size}px;border-radius:50%;
      background:${color};border:2px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,.4);
      display:flex;align-items:center;justify-content:center;">
      <div style="width:8px;height:8px;border-radius:50%;background:#fff;"></div>
    </div>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
}

function FitOnChange({ points }: { points: RoutePoint[] }) {
  const map = useMap();
  useEffect(() => {
    if (points.length === 0) return;
    if (points.length === 1) {
      map.setView([points[0].lat, points[0].lng], Math.max(map.getZoom(), 13));
    } else {
      map.fitBounds(points.map((p) => [p.lat, p.lng] as [number, number]), { padding: [40, 40] });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(points)]);
  return null;
}

export function VehicleMap({
  markers,
  route,
  selectedId,
  onSelect,
  height = 420,
}: {
  markers: MapMarker[];
  /** A historical route to draw as a polyline — distinct in color from the live markers. */
  route?: RoutePoint[];
  selectedId?: number;
  onSelect?: (id: number) => void;
  height?: number;
}) {
  const fitPoints = route && route.length > 0 ? route : markers.map((m) => ({ lat: m.lat, lng: m.lng }));
  const center: [number, number] = fitPoints.length > 0 ? [fitPoints[0].lat, fitPoints[0].lng] : [11.0168, 76.9558];

  return (
    <div style={{ height, borderRadius: 8, overflow: 'hidden' }}>
      <MapContainer center={center} zoom={12} style={{ height: '100%', width: '100%' }} scrollWheelZoom>
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <FitOnChange points={fitPoints} />
        {route && route.length > 1 && (
          <Polyline positions={route.map((p) => [p.lat, p.lng])} pathOptions={{ color: '#3A6D80', weight: 4, opacity: 0.8 }} />
        )}
        {markers.map((m) => (
          <Marker
            key={m.id}
            position={[m.lat, m.lng]}
            icon={icon(m.online, m.id === selectedId)}
            eventHandlers={{ click: () => onSelect?.(m.id) }}
          >
            <Popup>
              <b>{m.label}</b>
              <br />
              {m.online ? 'Online' : 'Offline'}
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}
