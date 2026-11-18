# Phase 1.5 UI redesign

## Summary

The React shell now uses a light enterprise dashboard theme: a white responsive top navigation, restrained blue actions, light-grey page background, white bordered cards, and explicit status/empty/loading/error states.

## Changed pages and components

- Dashboard: overview header, unavailable metric cards, honest recent-scan and pending-review empty states, and live backend system-info state.
- Media Scans: keyboard-accessible image selection/drop zone, client-side format validation for JPEG/JPG, PNG, and WebP, and honest inactive scan/result states.
- Rights Registry, Policies, Reports, and Audit History: consistent frontend-only placeholders.
- Shared UI: page headers, cards, metrics, empty/loading/error states.

## Verified API integration

The only implemented frontend API call remains `GET /api/v1/system/info`. It is read through `VITE_API_BASE_URL` and drives the dashboard connection state.

The proposed dashboard metrics, scans, queue, upload, resolution, and results endpoints are not implemented in the current Spring Boot service and were not called.

## Limitations

- No authentication, upload, scan, rights, policy, report, or audit workflow exists yet.
- The selected image is validated locally only and is never transmitted.
- Scan search and filters are visibly disabled until records are supplied by a real backend API.
- Browser-level route and responsive testing requires a locally started frontend; no browser automation is configured.

## Validation

- All four Phase 0 JSON contract files were parsed successfully after the redesign.
- A source audit confirms that the frontend still calls only `GET /api/v1/system/info` through `VITE_API_BASE_URL`.
- `npm run build` and `npm run lint` could not run in the current sandbox because Node was denied access to the local user-profile path. Run them from the developer terminal to verify the updated frontend bundle.
