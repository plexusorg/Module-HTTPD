<script lang="ts">
    interface Props {
        uuid: string;
        size?: number;
    }

    let {uuid, size = 48}: Props = $props();
</script>

<span class="relative inline-block shrink-0 overflow-hidden rounded-lg bg-muted"
      style:width="{size}px" style:height="{size}px" aria-hidden="true">
    <svg viewBox="0 0 8 8" class="size-full" shape-rendering="crispEdges" aria-hidden="true">
        <path fill="#b88765" d="M0 0h8v8H0z"/>
        <path fill="#493125" d="M0 0h8v2H0zM0 2h1v1H0zM7 2h1v1H7z"/>
        <path fill="#f1e7dc" d="M1 4h2v1H1zM5 4h2v1H5z"/>
        <path fill="#536094" d="M2 4h1v1H2zM5 4h1v1H5z"/>
        <path fill="#946044" d="M3 5h2v1H3z"/>
        <path fill="#593b2c" d="M2 6h1v2H2zM5 6h1v2H5zM3 7h2v1H3z"/>
    </svg>
    {#key uuid}
        <img class="absolute inset-0 size-full inventory-pixelated"
             src={`https://vzge.me/face/512/${encodeURIComponent(uuid)}.png`}
             alt="" loading="lazy" width={size} height={size}
             onerror={(event) => { event.currentTarget.setAttribute('hidden', ''); }}/>
    {/key}
</span>
