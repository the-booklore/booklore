import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, DetachedRouteHandle, RouteReuseStrategy } from '@angular/router';

@Injectable({
  providedIn: 'root',
})
export class CustomReuseStrategy implements RouteReuseStrategy {
  private storedRoutes = new Map<string, DetachedRouteHandle>();

  // Only detach the route if it's for the book details page
  shouldDetach(route: ActivatedRouteSnapshot): boolean {
    return route.routeConfig?.path === 'book/:id'; // Match the path of the route you want to reuse
  }

  // Store the route component instance when detaching
  store(route: ActivatedRouteSnapshot, handle: DetachedRouteHandle | null): void {
    if (handle) {
      // Save the handle if we are detaching this route
      this.storedRoutes.set(route.routeConfig?.path || '', handle);
    }
  }

  // Check if we should attach the route (reuse it) when navigating back to it
  shouldAttach(route: ActivatedRouteSnapshot): boolean {
    // Attach the route only if there's a stored instance for this route
    return !!this.storedRoutes.get(route.routeConfig?.path || '');
  }

  // Retrieve the stored route component instance
  retrieve(route: ActivatedRouteSnapshot): DetachedRouteHandle | null {
    return this.storedRoutes.get(route.routeConfig?.path || '') || null;
  }

  // Determine if the route should be reused based on its configuration
  shouldReuseRoute(future: ActivatedRouteSnapshot, curr: ActivatedRouteSnapshot): boolean {
    // Reuse the route if the path and parameters match
    return future.routeConfig === curr.routeConfig && future.params['id'] === curr.params['id'];
  }
}
